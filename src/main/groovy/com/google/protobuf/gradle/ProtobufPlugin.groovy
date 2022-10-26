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

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The main class for the protobuf plugin.
 */
@CompileStatic
class ProtobufPlugin implements Plugin<Project> {
  // any one of these plugins should be sufficient to proceed with applying this plugin
  private static final List<String> PREREQ_PLUGIN_OPTIONS = [
    'java',
    'java-library',
    'com.android.application',
    'com.android.library',
    'com.android.instantapp',
    'com.android.feature',
    'com.android.dynamic-feature',
  ]

  private Project project
  private ProtobufExtension protobufExtension
  private final AtomicBoolean wasApplied = new AtomicBoolean(false)

  void apply(final Project project) {
    project.pluginManager.apply(ProtobufConventionPlugin)

    this.project = project
    this.protobufExtension = project.extensions.getByType(ProtobufExtension)

    // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
    // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
    // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
    // has been applied then we will assume that none of prerequisite plugins were specified and we will
    // throw an Exception to alert the user of this configuration issue.
    Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { AppliedPlugin prerequisitePlugin ->
      if (wasApplied.getAndSet(true)) {
        project.logger.info('The com.google.protobuf plugin was already applied to the project: ' + project.path
          + ' and will not be applied again after plugin: ' + prerequisitePlugin.id)
      } else {
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
        doJvmApply()
      }
    }

  private void doJvmApply() {
    // Java projects will extract included protos from a 'compileProtoPath'
    // configuration of each source set, while Android projects will
    // extract included protos from {@code variant.compileConfiguration}
    // of each variant.
    Collection<Closure> postConfigure = []

    project.extensions.getByType(SourceSetContainer).configureEach { SourceSet sourceSet ->
      ProtoSourceSet protoSourceSet = this.protobufExtension.sourceSets.create(sourceSet.name)
      protoSourceSet.proto.include("src/${sourceSet.name}/proto")
      sourceSet.extensions.add("proto", protoSourceSet.proto)

      Configuration compileProtoPath = createCompileProtoPathConfiguration(sourceSet.name)
      this.addTasksForSourceSet(sourceSet, protoSourceSet, compileProtoPath, postConfigure)
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
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private void addTasksForSourceSet(
        final SourceSet sourceSet,
        final ProtoSourceSet protoSourceSet,
        final Configuration compileProtoPath,
        final Collection<Closure> postConfigure
    ) {
      // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
      // 'resources' of the output of 'main', in which the source protos are placed.  This is
      // nicer than the ad-hoc solution that Android has, because it works for any extended
      // configuration, not just 'testCompile'.
      Provider<ProtobufExtract> extractIncludeProtosTask = setupExtractIncludeProtosTask(
        sourceSet.name,
        compileProtoPath,
        sourceSet.compileClasspath
      )
      Provider<GenerateProtoTask> generateProtoTask = addGenerateProtoTask(
        sourceSet.name,
        protoSourceSet.proto,
        project.files(project.tasks.named(protoSourceSet.extractProtosTaskName)),
        extractIncludeProtosTask
      ) { GenerateProtoTask task ->
        task.sourceSet = sourceSet
        task.doneInitializing()
        task.builtins.maybeCreate("java")
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
          protoSourceSet.proto.srcDirs.each { File protoDir ->
            Utils.addToIdeSources(project, isTest, protoDir, false)
          }
          Utils.addToIdeSources(
            project,
            isTest,
            project.files(project.tasks.named(protoSourceSet.extractProtosTaskName)).singleFile,
            true
          )
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
}
