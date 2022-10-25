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

import com.google.protobuf.gradle.internal.ProjectExt
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.jvm.tasks.ProcessResources

/**
 * The main class for the protobuf plugin.
 */
@CompileStatic
class ProtobufPlugin implements Plugin<Project> {
    // any one of these plugins should be sufficient to proceed with applying this plugin
    private static final List<String> PREREQ_PLUGIN_OPTIONS = ['java', 'java-library'] +
      ProtobufAndroidPlugin.PREREQ_PLUGIN_OPTIONS

    private Project project
    private ProtobufExtension protobufExtension
    private boolean wasApplied = false

    void apply(final Project project) {
      ProjectExt.checkMinimalGradleVersion(project)

      this.protobufExtension = project.extensions.create("protobuf", ProtobufExtension, project)

      this.project = project

      // Provides the osdetector extension
      project.apply([plugin:com.google.gradle.osdetector.OsDetectorPlugin])

        // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
        // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
        // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
        // has been applied then we will assume that none of prerequisite plugins were specified and we will
        // throw an Exception to alert the user of this configuration issue.
        Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { AppliedPlugin prerequisitePlugin ->
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

    private void doApply() {
      if (Utils.isAndroidProject(project)) {
        this.project.pluginManager.apply(ProtobufAndroidPlugin)
      } else {
        // Java projects will extract included protos from a 'compileProtoPath'
        // configuration of each source set, while Android projects will
        // extract included protos from {@code variant.compileConfiguration}
        // of each variant.
        Collection<Closure> postConfigure = []

        project.extensions.getByType(SourceSetContainer).configureEach { SourceSet sourceSet ->
          SourceDirectorySet protoSrcDirSet = addSourceSetExtension(sourceSet)
          Configuration protobufConfig = createProtobufConfiguration(sourceSet.name)
          Configuration compileProtoPath = createCompileProtoPathConfiguration(sourceSet.name)
          this.addTasksForSourceSet(sourceSet, protoSrcDirSet, protobufConfig, compileProtoPath, postConfigure)
        }

        project.afterEvaluate {
          this.protobufExtension.configureTasks()
          // Disallow user configuration outside the config closures, because the operations just
          // after the doneConfig() loop over the generated outputs and will be out-of-date if
          // plugin output is added after this point.
          this.protobufExtension.generateProtoTasks.all().configureEach { it.doneConfig() }
          postConfigure.each { it.call() }
          // protoc and codegen plugin configuration may change through the protobuf{}
          // block. Only at this point the configuration has been finalized.
          this.protobufExtension.tools.resolve(project)
        }
      }
    }

    /**
     * Creates a 'protobuf' configuration for the given source set. The build author can
     * configure dependencies for it. The extract-protos task of each source set will
     * extract protobuf files from dependencies in this configuration.
     */
    private Configuration createProtobufConfiguration(String sourceSetName) {
      String protobufConfigName = Utils.getConfigName(sourceSetName, 'protobuf')
      return project.configurations.create(protobufConfigName) { Configuration it ->
        it.visible = false
        it.transitive = true
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
    private Configuration createCompileProtoPathConfiguration(String sourceSetName) {
      String compileProtoConfigName = Utils.getConfigName(sourceSetName, 'compileProtoPath')
      Configuration compileConfig =
              project.configurations.getByName(Utils.getConfigName(sourceSetName, 'compileOnly'))
      Configuration implementationConfig =
              project.configurations.getByName(Utils.getConfigName(sourceSetName, 'implementation'))
      return project.configurations.create(compileProtoConfigName) { Configuration it ->
          it.visible = false
          it.transitive = true
          it.extendsFrom = [compileConfig, implementationConfig]
          it.canBeConsumed = false
          it.getAttributes()
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
    private SourceDirectorySet addSourceSetExtension(SourceSet sourceSet) {
      String name = sourceSet.name
      SourceDirectorySet sds = project.objects.sourceDirectorySet(name, "${name} Proto source")
      sourceSet.extensions.add('proto', sds)
      sds.srcDir("src/${name}/proto")
      sds.include("**/*.proto")
      return sds
    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private void addTasksForSourceSet(
        final SourceSet sourceSet,
        final SourceDirectorySet protoSrcDirSet,
        final Configuration protobufConfig,
        final Configuration compileProtoPath,
        final Collection<Closure> postConfigure
    ) {
      Provider<ProtobufExtract> extractProtosTask =
          setupExtractProtosTask(sourceSet.name, protobufConfig)
      // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
      // 'resources' of the output of 'main', in which the source protos are placed.  This is
      // nicer than the ad-hoc solution that Android has, because it works for any extended
      // configuration, not just 'testCompile'.
      Provider<ProtobufExtract> extractIncludeProtosTask = setupExtractIncludeProtosTask(
          sourceSet.name, compileProtoPath, sourceSet.compileClasspath)
      Provider<GenerateProtoTask> generateProtoTask = addGenerateProtoTask(
          sourceSet.name, protoSrcDirSet, project.files(extractProtosTask),
          extractIncludeProtosTask) {
        it.sourceSet = sourceSet
        it.doneInitializing()
        it.builtins.maybeCreate("java")
      }

      sourceSet.java.source(sourceDirectorySetForGenerateProtoTask(sourceSet.name, generateProtoTask))

      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      project.tasks.named(sourceSet.getTaskName('process', 'resources'), ProcessResources).configure {
        it.from(generateProtoTask.get().sourceDirs) { CopySpec cs ->
          cs.include '**/*.proto'
        }
      }

      postConfigure.add {
        project.plugins.withId("eclipse") {
          // This is required because the intellij/eclipse plugin does not allow adding source directories
          // that do not exist. The intellij/eclipse config files should be valid from the start.
          generateProtoTask.get().getOutputSourceDirectories().each { File outputDir ->
            outputDir.mkdirs()
          }
        }

        project.plugins.withId("idea") {
          boolean isTest = Utils.isTest(sourceSet.name)
          protoSrcDirSet.srcDirs.each { File protoDir ->
            Utils.addToIdeSources(project, isTest, protoDir, false)
          }
          Utils.addToIdeSources(project, isTest, project.files(extractProtosTask).singleFile, true)
          Utils.addToIdeSources(project, isTest, project.files(extractIncludeProtosTask).singleFile, true)
          generateProtoTask.get().getOutputSourceDirectories().each { File outputDir ->
            Utils.addToIdeSources(project, isTest, outputDir, true)
          }
        }
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
    @SuppressWarnings(["UnnecessaryObjectReferences"]) // suppress a lot of it.doLogic in task registration block
    private Provider<GenerateProtoTask> addGenerateProtoTask(
        String sourceSetOrVariantName,
        SourceDirectorySet protoSourceSet,
        FileCollection extractProtosDirs,
        Provider<ProtobufExtract> extractIncludeProtosTask,
        Action<GenerateProtoTask> configureAction) {
      String generateProtoTaskName = 'generate' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
      Provider<String> outDir = project.providers.provider {
        "${this.protobufExtension.generatedFilesBaseDir}/${sourceSetOrVariantName}".toString()
      }
      return project.tasks.register(generateProtoTaskName, GenerateProtoTask) {
        it.description = "Compiles Proto source for '${sourceSetOrVariantName}'".toString()
        it.outputBaseDir = outDir
        it.addSourceDirs(protoSourceSet)
        it.addIncludeDir(protoSourceSet.sourceDirectories)
        it.addSourceDirs(extractProtosDirs)
        it.addIncludeDir(extractProtosDirs)
        it.addIncludeDir(project.files(extractIncludeProtosTask))
        configureAction.execute(it)
      }
    }

    /**
     * Generate a SourceDirectorySet for a GenerateProtoTask that includes just
     * Java and Kotlin source files. Build dependencies are properly plumbed.
     */
    private SourceDirectorySet sourceDirectorySetForGenerateProtoTask(
        String sourceSetName, Provider<GenerateProtoTask> generateProtoTask) {
      String srcDirSetName = 'generate-proto-' + sourceSetName
      SourceDirectorySet srcDirSet = project.objects.sourceDirectorySet(srcDirSetName, srcDirSetName)
      srcDirSet.srcDirs project.objects.fileCollection()
          .builtBy(generateProtoTask)
          .from(project.providers.provider {
            generateProtoTask.get().getOutputSourceDirectories()
          })
      srcDirSet.include("**/*.java", "**/*.kt")
      return srcDirSet
    }

    /**
     * Sets up a task to extract protos from protobuf dependencies. They are
     * treated as sources and will be compiled.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    private Provider<ProtobufExtract> setupExtractProtosTask(
        final String sourceSetName, Configuration protobufConfig) {
      return project.tasks.register(getExtractProtosTaskName(sourceSetName), ProtobufExtract) {
        it.description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
        it.destDir.set(getExtractedProtosDir(sourceSetName) as File)
        it.inputFiles.from(protobufConfig)
      }
    }

    private String getExtractProtosTaskName(String sourceSetName) {
      return 'extract' + Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Proto'
    }

    /**
     * Sets up a task to extract protos from compile dependencies of a sourceSet, Those are needed
     * for imports in proto files, but they won't be compiled since they have already been compiled
     * in their own projects or artifacts.
     *
     * <p>This task is per-sourceSet for both Java and per variant for Android.
     */
    private Provider<ProtobufExtract> setupExtractIncludeProtosTask(
        String sourceSetOrVariantName,
        FileCollection compileClasspathConfiguration,
        FileCollection testedCompileClasspathConfiguration) {
      String extractIncludeProtosTaskName = 'extractInclude' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
      return project.tasks.register(extractIncludeProtosTaskName, ProtobufExtract) {
        it.description = "Extracts proto files from compile dependencies for includes"
        it.destDir.set(getExtractedIncludeProtosDir(sourceSetOrVariantName) as File)
        it.inputFiles.from(compileClasspathConfiguration)

        // TL; DR: Make protos in 'test' sourceSet able to import protos from the 'main'
        // sourceSet.  Sub-configurations, e.g., 'testCompile' that extends 'compile', don't
        // depend on the their super configurations. As a result, 'testCompile' doesn't depend on
        // 'compile' and it cannot get the proto files from 'main' sourceSet through the
        // configuration.
        it.inputFiles.from(testedCompileClasspathConfiguration)
      }
    }

    private String getExtractedIncludeProtosDir(String sourceSetName) {
      return "${project.buildDir}/extracted-include-protos/${sourceSetName}"
    }

    private String getExtractedProtosDir(String sourceSetName) {
      return "${project.buildDir}/extracted-protos/${sourceSetName}"
    }
}
