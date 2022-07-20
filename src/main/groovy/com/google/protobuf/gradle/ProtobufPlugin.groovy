/*
 * Original work copyright (c) 2015, Alex Antonov. All rights reserved.
 * Modified work copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.protobuf.gradle

import com.google.common.collect.ImmutableList
import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GradleVersion

import javax.inject.Inject

/**
 * The main class for the protobuf plugin.
 */
@CompileDynamic
class ProtobufPlugin implements Plugin<Project> {
    // any one of these plugins should be sufficient to proceed with applying this plugin
    private static final List<String> PREREQ_PLUGIN_OPTIONS = [
            'java',
            'java-library',
            'com.android.application',
            'com.android.feature',
            'com.android.library',
            'android',
            'android-library',
    ]

    private static final List<String> SUPPORTED_LANGUAGES = [
        'java',
        'kotlin',
    ]

    private final FileResolver fileResolver
    private Project project
    private ProtobufExtension protobufExtension
    private boolean wasApplied = false

    @Inject
    public ProtobufPlugin(FileResolver fileResolver) {
      this.fileResolver = fileResolver
    }

    void apply(final Project project) {
      if (GradleVersion.current() < GradleVersion.version("5.6")) {
        throw new GradleException(
          "Gradle version is ${project.gradle.gradleVersion}. Minimum supported version is 5.6")
      }

      this.protobufExtension = project.extensions.create("protobuf", ProtobufExtension, project)

      this.project = project

      // Provides the osdetector extension
      project.apply([plugin:com.google.gradle.osdetector.OsDetectorPlugin])

        // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
        // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
        // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
        // has been applied then we will assume that none of prerequisite plugins were specified and we will
        // throw an Exception to alert the user of this configuration issue.
        Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { prerequisitePlugin ->
          if (wasApplied) {
            project.logger.info('The com.google.protobuf plugin was already applied to the project: ' + project.path
                + ' and will not be applied again after plugin: ' + prerequisitePlugin.id)
          } else {
            wasApplied = true

            doApply()
          }
        }

        PREREQ_PLUGIN_OPTIONS.each { pluginName ->
          project.pluginManager.withPlugin(pluginName, applyWithPrerequisitePlugin)
        }

        project.afterEvaluate {
          if (!wasApplied) {
            throw new GradleException('The com.google.protobuf plugin could not be applied during project evaluation.'
                + ' The Java plugin or one of the Android plugins must be applied to the project first.')
          }
        }
    }

    private static void linkGenerateProtoTasksToTask(Task task, GenerateProtoTask genProtoTask) {
      task.dependsOn(genProtoTask)
    }

    private void doApply() {
        boolean isAndroid = Utils.isAndroidProject(project)
        // Java projects will extract included protos from a 'compileProtoPath'
        // configuration of each source set, while Android projects will
        // extract included protos from {@code variant.compileConfiguration}
        // of each variant.
        Collection<Closure> postConfigure = []
        if (isAndroid) {
          project.android.sourceSets.configureEach { sourceSet ->
            addSourceSetExtension(sourceSet)
            createProtobufConfiguration(sourceSet.name)
          }
          getNonTestVariants().configureEach { variant ->
            addTasksForVariant(variant, false, postConfigure)
          }
          project.android.unitTestVariants.configureEach { variant ->
            addTasksForVariant(variant, true, postConfigure)
          }
          project.android.testVariants.configureEach { variant ->
            addTasksForVariant(variant, true, postConfigure)
          }
        } else {
          project.sourceSets.configureEach { sourceSet ->
            addSourceSetExtension(sourceSet)
            createProtobufConfiguration(sourceSet.name)
            createCompileProtoPathConfiguration(sourceSet.name)
            addTasksForSourceSet(sourceSet)
          }
        }
        project.afterEvaluate {
          this.protobufExtension.configureTasks()
          // Disallow user configuration outside the config closures, because the operations just
          // after the doneConfig() loop over the generated outputs and will be out-of-date if
          // plugin output is added after this point.
          this.protobufExtension.generateProtoTasks.all()*.doneConfig()
          postConfigure.each { it.call() }
          // protoc and codegen plugin configuration may change through the protobuf{}
          // block. Only at this point the configuration has been finalized.
          this.protobufExtension.tools.registerTaskDependencies(this.protobufExtension.generateProtoTasks.all())

          // Register proto and generated sources with IDE
          addSourcesToIde(isAndroid)
        }
    }

    /**
     * Creates a 'protobuf' configuration for the given source set. The build author can
     * configure dependencies for it. The extract-protos task of each source set will
     * extract protobuf files from dependencies in this configuration.
     */
    private void  createProtobufConfiguration(String sourceSetName) {
      String protobufConfigName = Utils.getConfigName(sourceSetName, 'protobuf')
      if (project.configurations.findByName(protobufConfigName) == null) {
        project.configurations.create(protobufConfigName) {
          visible = false
          transitive = true
          extendsFrom = []
        }
      }
    }

    /**
     * Creates an internal 'compileProtoPath' configuration for the given source set that extends
     * compilation configurations as a bucket of dependencies with resources attribute.
     * The extract-include-protos task of each source set will extract protobuf files from
     * resolved dependencies in this configuration.
     *
     * <p> For Java projects only.
     * <p> This works around 'java-library' plugin not exposing resources to consumers for compilation.
     */
    private void createCompileProtoPathConfiguration(String sourceSetName) {
      String compileProtoConfigName = Utils.getConfigName(sourceSetName, 'compileProtoPath')
      Configuration compileConfig =
              project.configurations.findByName(Utils.getConfigName(sourceSetName, 'compileOnly'))
      Configuration implementationConfig =
              project.configurations.findByName(Utils.getConfigName(sourceSetName, 'implementation'))
      if (project.configurations.findByName(compileProtoConfigName) == null) {
        project.configurations.create(compileProtoConfigName) {
            visible = false
            transitive = true
            extendsFrom = [compileConfig, implementationConfig]
            canBeConsumed = false
        }.getAttributes()
                // Variant attributes are not inherited. Setting it too loosely can
                // result in ambiguous variant selection errors.
                // CompileProtoPath only need proto files from dependency's resources.
                // LibraryElement "resources" is compatible with "jar" (if a "resources" variant is
                // not found, the "jar" variant will be used).
                .attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        project.getObjects().named(LibraryElements, LibraryElements.RESOURCES))
                // Although variants with any usage has proto files, not setting usage attribute
                // can result in ambiguous variant selection if the producer provides multiple
                // variants with different usage attribute.
                // Preserve the usage attribute from CompileOnly and Implementation.
                .attribute(
                        Usage.USAGE_ATTRIBUTE,
                        project.getObjects().named(Usage, Usage.JAVA_RUNTIME))
      }
    }

    /**
     * Adds the proto extension to the SourceSet, e.g., it creates
     * sourceSets.main.proto and sourceSets.test.proto.
     */
    private void addSourceSetExtension(Object sourceSet) {
      String name = sourceSet.name
      SourceDirectorySet sds = project.objects.sourceDirectorySet(name, "${name} Proto source")
      sourceSet.extensions.add('proto', sds)
      sds.srcDir("src/${name}/proto")
      sds.include("**/*.proto")
    }

    private Object getNonTestVariants() {
      return project.android.hasProperty('libraryVariants') ?
          project.android.libraryVariants : project.android.applicationVariants
    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private void addTasksForSourceSet(final SourceSet sourceSet) {
      GenerateProtoTask generateProtoTask = addGenerateProtoTask(sourceSet.name, [sourceSet])
      sourceSet.java.srcDirs(generateProtoTask.outputSourceDirectorySet)
      generateProtoTask.sourceSet = sourceSet
      generateProtoTask.doneInitializing()
      generateProtoTask.builtins {
        java { }
      }

      Task extractTask = setupExtractProtosTask(generateProtoTask, sourceSet.name)
      setupExtractIncludeProtosTask(generateProtoTask, sourceSet.name)

      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      Task processResourcesTask =
          project.tasks.getByName(sourceSet.getTaskName('process', 'resources'))
      processResourcesTask.from(generateProtoTask.sourceFiles) {
        include '**/*.proto'
      }
      processResourcesTask.dependsOn(extractTask)

      SUPPORTED_LANGUAGES.each { String lang ->
        linkGenerateProtoTasksToTaskName(sourceSet.getCompileTaskName(lang), generateProtoTask)
      }
    }

    /**
     * Creates Protobuf tasks for a variant in an Android project.
     */
    private void addTasksForVariant(final Object variant, boolean isTestVariant, Collection<Closure> postConfigure) {
      // GenerateProto task, one per variant (compilation unit).
      GenerateProtoTask generateProtoTask = addGenerateProtoTask(variant.name, variant.sourceSets)
      variant.sourceSets[Utils.variantNameToSourceSetName(variant.name)].java
        .srcDirs(generateProtoTask.outputSourceDirectorySet)
      generateProtoTask.setVariant(variant, isTestVariant)
      generateProtoTask.flavors = ImmutableList.copyOf(variant.productFlavors.collect { it.name } )
      if (variant.hasProperty('buildType')) {
        generateProtoTask.buildType = variant.buildType.name
      }
      generateProtoTask.doneInitializing()

      // ExtractIncludeProto task, one per variant (compilation unit).
      // Proto definitions from an AAR dependencies are in its JAR resources.
      Attribute artifactType = Attribute.of("artifactType", String)
      FileCollection classPathConfig = variant.compileConfiguration.incoming.artifactView {
        attributes {
            it.attribute(artifactType, "jar")
        }
      }.files
      FileCollection testClassPathConfig =
          variant.hasProperty("testedVariant") ?
            variant.testedVariant.compileConfiguration.incoming.artifactView {
                attributes {
                    it.attribute(artifactType, "jar")
                }
            }.files : null
      setupExtractIncludeProtosTask(generateProtoTask, variant.name, true, classPathConfig, testClassPathConfig)

      // ExtractProto task, one per source set.
      if (project.android.hasProperty('libraryVariants')) {
          // Include source proto files in the compiled archive, so that proto files from
          // dependent projects can import them.
          Task processResourcesTask = variant.getProcessJavaResourcesProvider().get()
          processResourcesTask.from(generateProtoTask.sourceFiles) {
              include '**/*.proto'
          }
          variant.sourceSets.each {
              Task extractTask = setupExtractProtosTask(generateProtoTask, it.name)
              processResourcesTask.dependsOn(extractTask)
          }
      } else {
          variant.sourceSets.each {
              setupExtractProtosTask(generateProtoTask, it.name)
          }
      }
      if (isTestVariant) {
        // unit test variants do not implement registerJavaGeneratingTask
        Task javaCompileTask = variant.javaCompileProvider.get()
        if (javaCompileTask != null) {
          linkGenerateProtoTasksToTask(javaCompileTask, generateProtoTask)
        }
      } else {
        postConfigure.add {
          // This cannot be called once task execution has started.
          variant.registerJavaGeneratingTask(generateProtoTask, generateProtoTask.getOutputSourceDirectories())
        }
      }
      postConfigure.add {
        linkGenerateProtoTasksToTaskName(
            Utils.getKotlinAndroidCompileTaskName(project, variant.name), generateProtoTask)
      }
    }

    /**
     * Adds a task to run protoc and compile all proto source files for a sourceSet or variant.
     *
     * @param sourceSetOrVariantName the name of the sourceSet (Java) or
     * variant (Android) that this task will run for.
     *
     * @param sourceSets the sourceSets that contains the proto files to be
     * compiled. For Java it's the sourceSet that sourceSetOrVariantName stands
     * for; for Android it's the collection of sourceSets that the variant includes.
     */
    private Task addGenerateProtoTask(String sourceSetOrVariantName, Collection<Object> sourceSets) {
      String generateProtoTaskName = 'generate' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
      Provider<String> outDir = project.providers.provider {
        "${this.protobufExtension.generatedFilesBaseDir}/${sourceSetOrVariantName}"
      }
      return project.tasks.create(generateProtoTaskName, GenerateProtoTask) {
        description = "Compiles Proto source for '${sourceSetOrVariantName}'"
        outputBaseDir = outDir
        it.fileResolver = this.fileResolver
        sourceSets.each { sourceSet ->
          addSourceFiles(sourceSet.proto)
          SourceDirectorySet protoSrcDirSet = sourceSet.proto
          addIncludeDir(protoSrcDirSet.sourceDirectories)
        }
        protocLocator.set(project.providers.provider { this.protobufExtension.tools.protoc })
        pluginsExecutableLocators.set(project.providers.provider {
            ((NamedDomainObjectContainer<ExecutableLocator>) this.protobufExtension.tools.plugins).asMap
        })
      }
    }

    /**
     * Sets up a task to extract protos from protobuf dependencies. They are
     * treated as sources and will be compiled.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    private Task setupExtractProtosTask(final GenerateProtoTask generateProtoTask, final String sourceSetName) {
      String extractProtosTaskName = 'extract' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Proto'
      Task task = project.tasks.findByName(extractProtosTaskName)
      if (task == null) {
        task = project.tasks.create(extractProtosTaskName, ProtobufExtract) {
          description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
          destDir = getExtractedProtosDir(sourceSetName) as File
          inputFiles.from(project.configurations[Utils.getConfigName(sourceSetName, 'protobuf')])
          isTest = Utils.isTest(sourceSetName)
        }
      }

      linkExtractTaskToGenerateTask(task, generateProtoTask)
      generateProtoTask.addSourceFiles(project.fileTree(task.destDir) { include "**/*.proto" })
      return task
    }

    /**
     * Sets up a task to extract protos from compile dependencies of a sourceSet, Those are needed
     * for imports in proto files, but they won't be compiled since they have already been compiled
     * in their own projects or artifacts.
     *
     * <p>This task is per-sourceSet for both Java and per variant for Android.
     */
    private void setupExtractIncludeProtosTask(
        GenerateProtoTask generateProtoTask,
        String sourceSetOrVariantName,
        boolean isAndroid = false,
        FileCollection compileClasspathConfiguration = null,
        FileCollection testedCompileClasspathConfiguration = null) {
      String extractIncludeProtosTaskName = 'extractInclude' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
      Task task = project.tasks.findByName(extractIncludeProtosTaskName)
      if (task == null) {
        task = project.tasks.create(extractIncludeProtosTaskName, ProtobufExtract) {
          description = "Extracts proto files from compile dependencies for includes"
          destDir = getExtractedIncludeProtosDir(sourceSetOrVariantName) as File
          inputFiles.from(compileClasspathConfiguration
            ?: project.configurations[Utils.getConfigName(sourceSetOrVariantName, 'compileProtoPath')])

          // TL; DR: Make protos in 'test' sourceSet able to import protos from the 'main'
          // sourceSet.  Sub-configurations, e.g., 'testCompile' that extends 'compile', don't
          // depend on the their super configurations. As a result, 'testCompile' doesn't depend on
          // 'compile' and it cannot get the proto files from 'main' sourceSet through the
          // configuration. However,
          if (isAndroid) {
            // TODO(zhangkun83): Android sourceSet doesn't have compileClasspath. If it did, we
            // haven't figured out a way to put source protos in 'resources'. For now we use an
            // ad-hoc solution that manually includes the source protos of 'main' and its
            // dependencies.
            if (Utils.isTest(sourceSetOrVariantName)) {
              inputFiles.from project.android.sourceSets['main'].proto.sourceDirectories
              inputFiles.from testedCompileClasspathConfiguration
            }
          } else {
            // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
            // 'resources' of the output of 'main', in which the source protos are placed.  This is
            // nicer than the ad-hoc solution that Android has, because it works for any extended
            // configuration, not just 'testCompile'.
            inputFiles.from project.sourceSets[sourceSetOrVariantName].compileClasspath
          }
          isTest = Utils.isTest(sourceSetOrVariantName)
        }
      }

      linkExtractTaskToGenerateTask(task, generateProtoTask)
    }

    private void linkExtractTaskToGenerateTask(ProtobufExtract extractTask, GenerateProtoTask generateTask) {
      generateTask.dependsOn(extractTask)
      generateTask.addIncludeDir(project.files(extractTask.destDir))
    }

    private void linkGenerateProtoTasksToTaskName(String compileTaskName, GenerateProtoTask genProtoTask) {
      Task compileTask = project.tasks.findByName(compileTaskName)
      if (compileTask != null) {
        linkGenerateProtoTasksToTask(compileTask, genProtoTask)
      } else {
        // It is possible for a compile task to not exist yet. For example, if someone applied
        // the proto plugin and then later applies the kotlin plugin.
        project.tasks.configureEach { Task task ->
          if (task.name == compileTaskName) {
            linkGenerateProtoTasksToTask(task, genProtoTask)
          }
        }
      }
    }

    private String getExtractedIncludeProtosDir(String sourceSetName) {
      return "${project.buildDir}/extracted-include-protos/${sourceSetName}"
    }

    private String getExtractedProtosDir(String sourceSetName) {
      return "${project.buildDir}/extracted-protos/${sourceSetName}"
    }

    /**
     * Adds proto sources and generated sources to supported IDE plugins.
     */
    private void addSourcesToIde(boolean isAndroid) {
      // The generated javalite sources have lint issues. This is fixed upstream but
      // there is still no release with the fix yet.
      //   https://github.com/google/protobuf/pull/2823
      if (isAndroid) {
        // variant.registerJavaGeneratingTask called earlier already registers the generated
        // sources for normal variants, but unit test variants work differently and do not
        // use registerJavaGeneratingTask. Let's call addJavaSourceFoldersToModel for all tasks
        // to ensure all variants (including unit test variants) have sources registered.
        project.tasks.withType(GenerateProtoTask).each { GenerateProtoTask generateProtoTask ->
          generateProtoTask.variant.addJavaSourceFoldersToModel(generateProtoTask.getOutputSourceDirectories())
        }

        // TODO(zpencer): add gen sources from cross project GenerateProtoTasks
        // This is an uncommon project set up but it is possible.
        // We must avoid using private android APIs to find subprojects that the variant depends
        // on, such as by walking through
        //   variant.variantData.variantDependency.compileConfiguration.allDependencies
        // Gradle.getTaskGraph().getDependencies() should allow us to walk the task graph,
        // but unfortunately that API is @Incubating. We can revisit it when it becomes stable.
        // https://docs.gradle.org/4.8/javadoc/org/gradle/api/execution/
        // TaskExecutionGraph.html#getDependencies-org.gradle.api.Task-

        // TODO(zpencer): find a way to make android studio aware of the .proto files
        // Simply adding the .proto dirs via addJavaSourceFoldersToModel does not seem to work.
      } else {
        // Make the proto source dirs known to IDEs
        project.sourceSets.each { sourceSet ->
          SourceDirectorySet protoSrcDirSet = sourceSet.proto
          protoSrcDirSet.srcDirs.each { File protoDir ->
            Utils.addToIdeSources(project, Utils.isTest(sourceSet.name), protoDir, false)
          }
        }
        // Make the extracted proto dirs known to IDEs
        project.tasks.withType(ProtobufExtract).each { ProtobufExtract extractProtoTask ->
          Utils.addToIdeSources(project, extractProtoTask.isTest, extractProtoTask.destDir, true)
        }
        // Make the generated code dirs known to IDEs
        project.tasks.withType(GenerateProtoTask).each { GenerateProtoTask generateProtoTask ->
          generateProtoTask.getOutputSourceDirectories().each { File outputDir ->
            Utils.addToIdeSources(project, generateProtoTask.isTest, outputDir, true)
          }
        }
      }
    }
}
