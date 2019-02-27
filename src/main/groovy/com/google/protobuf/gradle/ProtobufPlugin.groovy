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
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

/**
 * The main class for the protobuf plugin.
 */
class ProtobufPlugin implements Plugin<Project> {
    // any one of these plugins should be sufficient to proceed with applying this plugin
    private static final List<String> PREREQ_PLUGIN_OPTIONS = [
            'java',
            'com.android.application',
            'com.android.feature',
            'com.android.library',
            'android',
            'android-library',
    ]

    private static final String USER_LANG_PROP = 'protobufGradlePluginAdditionalLanguages'
    private static final List<String> SUPPORTED_LANGUAGES = [
        'java',
        'kotlin',
    ]

    private final FileResolver fileResolver
    private Project project
    private boolean wasApplied = false

    @Inject
    public ProtobufPlugin(FileResolver fileResolver) {
      this.fileResolver = fileResolver
    }

    void apply(final Project project) {
      if (Utils.compareGradleVersion(project, "3.0") < 0) {
        throw new GradleException(
          "Gradle version is ${project.gradle.gradleVersion}. Minimum supported version is 3.0")
      }

        this.project = project
        // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
        // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
        // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
        // has been applied then we will assume that none of prerequisite plugins were specified and we will
        // throw an Exception to alert the user of this configuration issue.
        Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { prerequisitePlugin ->
          if (wasApplied) {
            project.logger.warn('The com.google.protobuf plugin was already applied to the project: ' + project.path
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

    private static List<String> getLanguages(Project project) {
      List<String> additionalLanguages = []
      if (project.hasProperty(USER_LANG_PROP)) {
        additionalLanguages = (List<String>) project.property(USER_LANG_PROP)
        project.logger.log(
            LogLevel.WARN,
            "protobuf plugin is now using additional unsupported languages: " + additionalLanguages)
      }
      return SUPPORTED_LANGUAGES + additionalLanguages
    }

    private static void linkGenerateProtoTasksToTask(Task task, GenerateProtoTask genProtoTask) {
      task.dependsOn(genProtoTask)
      task.source genProtoTask.getOutputSourceDirectorySet()
    }

    private void doApply() {
        // Provides the osdetector extension
        project.apply([plugin:com.google.gradle.osdetector.OsDetectorPlugin])

        project.convention.plugins.protobuf = new ProtobufConvention(project, fileResolver)

        addSourceSetExtensions()
        getSourceSets().all { sourceSet ->
          createConfiguration(sourceSet.name)
        }
        project.afterEvaluate {
          // The Android variants are only available at this point.
          addProtoTasks()
          project.protobuf.runTaskConfigClosures()
          // Disallow user configuration outside the config closures, because
          // next in linkGenerateProtoTasksToSourceCompile() we add generated,
          // outputs to the inputs of javaCompile tasks, and any new codegen
          // plugin output added after this point won't be added to javaCompile
          // tasks.
          project.protobuf.generateProtoTasks.all()*.doneConfig()
          linkGenerateProtoTasksToSourceCompile()
          // protoc and codegen plugin configuration may change through the protobuf{}
          // block. Only at this point the configuration has been finalized.
          project.protobuf.tools.registerTaskDependencies(project.protobuf.getGenerateProtoTasks().all())

          // Register proto and generated sources with IDE
          addSourcesToIde()
        }
    }

    /**
     * Creates a configuration if necessary for a source set so that the build
     * author can configure dependencies for it.
     */
    private void  createConfiguration(String sourceSetName) {
      String configName = Utils.getConfigName(sourceSetName, 'protobuf')
      if (project.configurations.findByName(configName) == null) {
        project.configurations.create(configName) {
          visible = false
          transitive = true
          extendsFrom = []
        }
      }
    }

    /**
     * Adds the proto extension to all SourceSets, e.g., it creates
     * sourceSets.main.proto and sourceSets.test.proto.
     */
    private void addSourceSetExtensions() {
      getSourceSets().all {  sourceSet ->
        String name = sourceSet.name
        SourceDirectorySet sds
        if (Utils.compareGradleVersion(project, "5.0") < 0) {
          // TODO(zhangkun83): remove dependency on Gradle internal APIs once we drop support for < 5.0
          sds = new DefaultSourceDirectorySet(
              name, "${name} Proto source", fileResolver, new DefaultDirectoryFileTreeFactory())
        } else {
          sds = project.objects.sourceDirectorySet(name, "${name} Proto source")
        }
        sourceSet.extensions.add('proto', sds)
        sds.srcDir("src/${name}/proto")
        sds.include("**/*.proto")
      }
    }

    /**
     * Returns the sourceSets container of a Java or an Android project.
     */
    private Object getSourceSets() {
      if (Utils.isAndroidProject(project)) {
        return project.android.sourceSets
      }
      return project.sourceSets
    }

    private Object getNonTestVariants() {
      return project.android.hasProperty('libraryVariants') ?
          project.android.libraryVariants : project.android.applicationVariants
    }

    /**
     * Adds Protobuf-related tasks to the project.
     */
    private void addProtoTasks() {
      if (Utils.isAndroidProject(project)) {
        getNonTestVariants().each { variant ->
          addTasksForVariant(variant, false)
        }
        (project.android.unitTestVariants + project.android.testVariants).each { variant ->
          addTasksForVariant(variant, true)
        }
      } else {
        getSourceSets().each { sourceSet ->
          addTasksForSourceSet(sourceSet)
        }
      }
    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private void addTasksForSourceSet(final SourceSet sourceSet) {
      Task generateProtoTask = addGenerateProtoTask(sourceSet.name, [sourceSet])
      generateProtoTask.sourceSet = sourceSet
      generateProtoTask.doneInitializing()
      generateProtoTask.builtins {
        java { }
      }

      setupExtractProtosTask(generateProtoTask, sourceSet.name)
      setupExtractIncludeProtosTask(generateProtoTask, sourceSet.name)

      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      Task processResourcesTask =
          project.tasks.getByName(sourceSet.getTaskName('process', 'resources'))
      processResourcesTask.from(generateProtoTask.sourceFiles) {
        include '**/*.proto'
      }
    }

    /**
     * Creates Protobuf tasks for a variant in an Android project.
     */
    private void addTasksForVariant(final Object variant, boolean isTestVariant) {
      Task generateProtoTask = addGenerateProtoTask(variant.name, variant.sourceSets)
      generateProtoTask.setVariant(variant, isTestVariant)
      generateProtoTask.flavors = ImmutableList.copyOf(variant.productFlavors.collect { it.name } )
      if (variant.hasProperty('buildType')) {
        generateProtoTask.buildType = variant.buildType.name
      }
      generateProtoTask.doneInitializing()

      variant.sourceSets.each {
        setupExtractProtosTask(generateProtoTask, it.name)
      }

      if (variant.hasProperty("compileConfiguration")) {
        // For Android Gradle plugin >= 2.5
        Attribute artifactType = Attribute.of("artifactType", String)
        String name = variant.name
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
        setupExtractIncludeProtosTask(generateProtoTask, name, classPathConfig, testClassPathConfig)
      } else {
        // For Android Gradle plugin < 2.5
        variant.sourceSets.each {
          setupExtractIncludeProtosTask(generateProtoTask, it.name)
        }
      }

      // TODO(zhangkun83): Include source proto files in the compiled archive,
      // so that proto files from dependent projects can import them.
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
      return project.tasks.create(generateProtoTaskName, GenerateProtoTask) {
        description = "Compiles Proto source for '${sourceSetOrVariantName}'"
        outputBaseDir = "${project.protobuf.generatedFilesBaseDir}/${sourceSetOrVariantName}"
        it.fileResolver = this.fileResolver
        sourceSets.each { sourceSet ->
          addSourceFiles(sourceSet.proto)
          SourceDirectorySet protoSrcDirSet = sourceSet.proto
          protoSrcDirSet.srcDirs.each { srcDir ->
            // The source directory designated from sourceSet may not actually exist on disk.
            // "include" it only when it exists, so that Gradle and protoc won't complain
            if (srcDir.exists()) {
              addIncludeDir(srcDir)
            }
          }
        }
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
    private void setupExtractProtosTask(
        GenerateProtoTask generateProtoTask, String sourceSetName) {
      String extractProtosTaskName = 'extract' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Proto'
      Task task = project.tasks.findByName(extractProtosTaskName)
      if (task == null) {
        task = project.tasks.create(extractProtosTaskName, ProtobufExtract) {
          description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
          destDir = getExtractedProtosDir(sourceSetName) as File
          inputs.files(project.configurations[Utils.getConfigName(sourceSetName, 'protobuf')])
                  .withPathSensitivity(PathSensitivity.NAME_ONLY)
          isTest = Utils.isTest(sourceSetName)
        }
      }

      linkExtractTaskToGenerateTask(task, generateProtoTask)
      generateProtoTask.addSourceFiles(project.fileTree(task.destDir) { include "**/*.proto" })
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
        FileCollection compileClasspathConfiguration = null,
        FileCollection testedCompileClasspathConfiguration = null) {
      String extractIncludeProtosTaskName = 'extractInclude' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
      Task task = project.tasks.findByName(extractIncludeProtosTaskName)
      if (task == null) {
        task = project.tasks.create(extractIncludeProtosTaskName, ProtobufExtract) {
          description = "Extracts proto files from compile dependencies for includes"
          destDir = getExtractedIncludeProtosDir(sourceSetOrVariantName) as File
          inputs.files (compileClasspathConfiguration
            ?: project.configurations[Utils.getConfigName(sourceSetOrVariantName, 'compile')])
                  .withPathSensitivity(PathSensitivity.NAME_ONLY)

          // TL; DR: Make protos in 'test' sourceSet able to import protos from the 'main'
          // sourceSet.  Sub-configurations, e.g., 'testCompile' that extends 'compile', don't
          // depend on the their super configurations. As a result, 'testCompile' doesn't depend on
          // 'compile' and it cannot get the proto files from 'main' sourceSet through the
          // configuration. However,
          if (Utils.isAndroidProject(project)) {
              //because compileClasspathConfiguration only contain jar  ( it.attribute(artifactType, "jar")  line 273),
              // but we want *.proto ,
              // so we have to get  dependencies project proto  Manually (eg:  implementation/api/compile project(':secondlib'))
              it.inputs.files project.files(getDependenceProjectProtoDir(project).asCollection())
            // TODO(zhangkun83): Android sourceSet doesn't have compileClasspath. If it did, we
            // haven't figured out a way to put source protos in 'resources'. For now we use an
            // ad-hoc solution that manually includes the source protos of 'main' and its
            // dependencies.
            if (Utils.isTest(sourceSetOrVariantName)) {
              inputs.files getSourceSets()['main'].proto
              inputs.files testedCompileClasspathConfiguration ?: project.configurations['compile']
            }
          } else {
            // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
            // 'resources' of the output of 'main', in which the source protos are placed.  This is
            // nicer than the ad-hoc solution that Android has, because it works for any extended
            // configuration, not just 'testCompile'.
            inputs.files (getSourceSets()[sourceSetOrVariantName].compileClasspath)
                    .withPathSensitivity(PathSensitivity.NAME_ONLY)
          }
          isTest = Utils.isTest(sourceSetOrVariantName)
        }
      }

      linkExtractTaskToGenerateTask(task, generateProtoTask)
    }

    private void linkExtractTaskToGenerateTask(ProtobufExtract extractTask, GenerateProtoTask generateTask) {
      generateTask.dependsOn(extractTask)
      generateTask.addIncludeDir(extractTask.destDir)
    }

    private void linkGenerateProtoTasksToTaskName(String compileTaskName, GenerateProtoTask genProtoTask) {
      Task compileTask = project.tasks.findByName(compileTaskName)
      if (compileTask != null) {
        linkGenerateProtoTasksToTask(compileTask, genProtoTask)
      } else {
        // It is possible for a compile task to not exist yet. For example, if someone applied
        // the proto plugin and then later applies the kotlin plugin.
        project.tasks.whenTaskAdded { Task task ->
          if (task.name == compileTaskName) {
            linkGenerateProtoTasksToTask(task, genProtoTask)
          }
        }
      }
    }

    private void linkGenerateProtoTasksToSourceCompile() {
      if (Utils.isAndroidProject(project)) {
        (getNonTestVariants() + project.android.testVariants).each { variant ->
          project.protobuf.generateProtoTasks.ofVariant(variant.name).each { GenerateProtoTask genProtoTask ->
            SourceDirectorySet generatedSources = genProtoTask.getOutputSourceDirectorySet()
            // This cannot be called once task execution has started.
            variant.registerJavaGeneratingTask(genProtoTask, generatedSources.source)
            linkGenerateProtoTasksToTaskName(
                Utils.getKotlinAndroidCompileTaskName(project, variant.name), genProtoTask)
          }
        }

        project.android.unitTestVariants.each { variant ->
          project.protobuf.generateProtoTasks.ofVariant(variant.name).each { GenerateProtoTask genProtoTask ->
            // unit test variants do not implement registerJavaGeneratingTask
            Task javaCompileTask = variant.javaCompile
            if (javaCompileTask != null) {
              linkGenerateProtoTasksToTask(javaCompileTask, genProtoTask)
            }

            linkGenerateProtoTasksToTaskName(
                Utils.getKotlinAndroidCompileTaskName(project, variant.name),
                genProtoTask)
          }
        }
      } else {
        project.sourceSets.each { SourceSet sourceSet ->
          project.protobuf.generateProtoTasks.ofSourceSet(sourceSet.name).each { GenerateProtoTask genProtoTask ->
            getLanguages(project).each { String lang ->
              linkGenerateProtoTasksToTaskName(sourceSet.getCompileTaskName(lang), genProtoTask)
            }
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
    private void addSourcesToIde() {
      // The generated javalite sources have lint issues. This is fixed upstream but
      // there is still no release with the fix yet.
      //   https://github.com/google/protobuf/pull/2823
      if (Utils.isAndroidProject(project)) {
        // variant.registerJavaGeneratingTask called earlier already registers the generated
        // sources for normal variants, but unit test variants work differently and do not
        // use registerJavaGeneratingTask. Let's call addJavaSourceFoldersToModel for all tasks
        // to ensure all variants (including unit test variants) have sources registered.
        project.tasks.withType(GenerateProtoTask).each { GenerateProtoTask generateProtoTask ->
          generateProtoTask.getOutputSourceDirectorySet().srcDirs.each { File outputDir ->
            generateProtoTask.variant.addJavaSourceFoldersToModel(outputDir)
          }
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
        getSourceSets().each { sourceSet ->
          SourceDirectorySet protoSrcDirSet = sourceSet.proto
          protoSrcDirSet.srcDirs.each { File protoDir ->
            Utils.addToIdeSources(project, Utils.isTest(sourceSet.name), protoDir)
          }
        }
        // Make the extracted proto dirs known to IDEs
        project.tasks.withType(ProtobufExtract).each { ProtobufExtract extractProtoTask ->
          Utils.addToIdeSources(project, extractProtoTask.isTest, extractProtoTask.destDir)
        }
        // Make the generated code dirs known to IDEs
        project.tasks.withType(GenerateProtoTask).each { GenerateProtoTask generateProtoTask ->
          generateProtoTask.getOutputSourceDirectorySet().srcDirs.each { File outputDir ->
            Utils.addToIdeSources(project, generateProtoTask.isTest, outputDir)
          }
        }
      }
    }

    private Set<String> getDependenceProjectProtoDir(Project project) {
        String slash = "/"//getSlash()
        Set<String> pathList = new String()[]//avoid repeat
        project.configurations.api.allDependencies.each {
            if (it instanceof ProjectDependency) {
                String path = it.dependencyProject.getProject().getProjectDir().path
                path += "${slash}src${slash}main${slash}proto"
                File file = new File(path)
                if (file.exists()) {
                    pathList.add(path)
                }
            }
        }

        project.configurations.implementation.allDependencies.each {
            if (it instanceof ProjectDependency) {
                String path = it.dependencyProject.getProject().getProjectDir().path
                path += "${slash}src${slash}main${slash}proto"
                File file = new File(path)
                if (file.exists()) {
                    pathList.add(path)
                }
            }
        }

        project.configurations.compile.allDependencies.each {
            if (it instanceof ProjectDependency) {
                String path = it.dependencyProject.getProject().getProjectDir().path
                path += "${slash}src${slash}main${slash}proto"
                File file = new File(path)
                if (file.exists()) {
                    pathList.add(path)
                }
            }
        }

        return pathList
    }
}
