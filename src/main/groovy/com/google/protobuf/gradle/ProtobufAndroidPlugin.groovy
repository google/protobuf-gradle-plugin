/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
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

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.google.gradle.osdetector.OsDetectorPlugin
import com.google.protobuf.gradle.internal.ProjectExt
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.SourceTask

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The main class for the protobuf plugin.
 */
@CompileStatic
class ProtobufAndroidPlugin implements Plugin<Project> {
  // any one of these plugins should be sufficient to proceed with applying this plugin
  @PackageScope
  static final List<String> PREREQ_PLUGIN_OPTIONS = [
    'com.android.application',
    'com.android.library',
    'com.android.instantapp',
    'com.android.feature',
    'com.android.dynamic-feature',
  ]

  private Project project
  private ProtobufExtension protobufExtension
  private final AtomicBoolean wasApplied = new AtomicBoolean(false)

  @SuppressWarnings(["SpaceAroundOperator"])
  // suppress a ternary operator formatting
  void apply(final Project project) {
    ProjectExt.checkMinimalGradleVersion(project)

    this.project = project
    this.protobufExtension = project.extensions.findByType(ProtobufExtension) == null
      ? project.extensions.create("protobuf", ProtobufExtension, project)
      : project.extensions.getByType(ProtobufExtension)

    project.pluginManager.apply(OsDetectorPlugin)

    Action<? super Plugin<Project>> androidPluginHandler = { plugin ->
      project.logger.debug("$plugin was applied to $project")
      if (!wasApplied.getAndSet(true)) {
        this.doApply()
      }
    }

    project.plugins.withId("com.android.application", androidPluginHandler)
    project.plugins.withId("com.android.library", androidPluginHandler)
    project.plugins.withId("com.android.instantapp", androidPluginHandler)
    project.plugins.withId("com.android.feature", androidPluginHandler)
    project.plugins.withId("com.android.dynamic-feature", androidPluginHandler)

    project.afterEvaluate {
      if (!wasApplied) {
        throw new GradleException("The com.google.protobuf-android plugin could not be applied to $project." +
          " One of the Android plugins must be applied to the $project.")
      }
    }
  }

  private static void linkGenerateProtoTasksToTask(SourceTask task, SourceDirectorySet srcDirSet) {
    task.source(srcDirSet)
  }

  private void doApply() {
    // Java projects will extract included protos from a 'compileProtoPath'
    // configuration of each source set, while Android projects will
    // extract included protos from {@code variant.compileConfiguration}
    // of each variant.
    Collection<Closure> postConfigure = []
    BaseExtension androidExtension = project.extensions.getByType(BaseExtension)
    androidExtension.sourceSets.configureEach { AndroidSourceSet sourceSet ->
      addSourceSetExtension(sourceSet)
      Configuration protobufConfig = createProtobufConfiguration(sourceSet.name)
      setupExtractProtosTask(sourceSet.name, protobufConfig)
    }
    ProjectExt.forEachVariant(this.project) { BaseVariant variant ->
      addTasksForVariant(
        variant,
        postConfigure
      )
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
   * Adds the proto extension to the SourceSet, e.g., it creates
   * sourceSets.main.proto and sourceSets.test.proto.
   */
  private SourceDirectorySet addSourceSetExtension(AndroidSourceSet sourceSet) {
    String name = sourceSet.name
    SourceDirectorySet sds = project.objects.sourceDirectorySet(name, "${name} Proto source")
    new DslObject(sourceSet).extensions.add('proto', sds)
    sds.srcDir("src/${name}/proto")
    sds.include("**/*.proto")
    return sds
  }

  /**
   * Creates Protobuf tasks for a variant in an Android project.
   */
  private void addTasksForVariant(final BaseVariant variant, Collection<Closure> postConfigure) {
    BaseExtension androidExtension = project.extensions.getByType(BaseExtension)

    // ExtractIncludeProto task, one per variant (compilation unit).
    // Proto definitions from an AAR dependencies are in its JAR resources.
    Attribute artifactType = Attribute.of("artifactType", String)
    FileCollection classPathConfig = variant.compileConfiguration.incoming
      .artifactView { ArtifactView.ViewConfiguration conf ->
        conf.attributes { AttributeContainer attrs ->
          attrs.attribute(artifactType, "jar")
        }
      }.files
    FileCollection testClassPathConfig = project.objects.fileCollection()
    if (variant instanceof TestVariant || variant instanceof UnitTestVariant) {
      // TODO(zhangkun83): Android sourceSet doesn't have compileClasspath. If it did, we
      // haven't figured out a way to put source protos in 'resources'. For now we use an
      // ad-hoc solution that manually includes the source protos of 'main' and its
      // dependencies.
      testClassPathConfig = ((androidExtension.sourceSets.getByName("main") as ExtensionAware)
        .extensions.getByName("proto") as SourceDirectorySet).sourceDirectories
      testClassPathConfig += (variant["testedVariant"] as BaseVariant).compileConfiguration.incoming
        .artifactView { ArtifactView.ViewConfiguration conf ->
          conf.attributes { AttributeContainer attrs ->
            attrs.attribute(artifactType, "jar")
          }
        }.files
    }
    Provider<ProtobufExtract> extractIncludeProtosTask =
      setupExtractIncludeProtosTask(variant.name, classPathConfig, testClassPathConfig)

    // GenerateProto task, one per variant (compilation unit).
    SourceDirectorySet sourceDirs = project.objects.sourceDirectorySet(variant.name, "AllSourceSets")
    variant.sourceSets.forEach { SourceProvider provider ->
      sourceDirs.source(((provider as ExtensionAware).extensions.getByName("proto") as SourceDirectorySet))
    }
    FileCollection extractProtosDirs = project.files(project.providers.provider {
      variant.sourceSets.collect {
        project.files(project.tasks.named(getExtractProtosTaskName(it.name)))
      }
    })
    Provider<GenerateProtoTask> generateProtoTask = addGenerateProtoTask(
      variant.name,
      sourceDirs,
      extractProtosDirs,
      extractIncludeProtosTask
    ) { GenerateProtoTask task ->
      // temporary hack for do not depend agp inside of GenerateProtoTask
      task.setVariant(variant, variant instanceof TestVariant || variant instanceof UnitTestVariant)
      task.flavors = variant.productFlavors.collect { ProductFlavor flavour -> flavour.name }
      if (variant.hasProperty('buildType')) {
        task.buildType = variant.buildType.name
      }
      task.doneInitializing()
    }

    if (androidExtension instanceof LibraryExtension) {
      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      variant.getProcessJavaResourcesProvider().configure { AbstractCopyTask task ->
        task.from(generateProtoTask.get().sourceDirs) { CopySpec spec ->
          spec.include('**/*.proto')
        }
      }
    }

    postConfigure.add {
      // This cannot be called once task execution has started.
      variant.registerJavaGeneratingTask(generateProtoTask.get(), generateProtoTask.get().getOutputSourceDirectories())
      linkGenerateProtoTasksToTaskName(
        Utils.getKotlinAndroidCompileTaskName(project, variant.name),
        sourceDirectorySetForGenerateProtoTask(variant.name, generateProtoTask)
      )
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
  @SuppressWarnings(["UnnecessaryObjectReferences"])
  // suppress a lot of it.doLogic in task registration block
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

  private void linkGenerateProtoTasksToTaskName(String compileTaskName, SourceDirectorySet srcDirSet) {
    try {
      project.tasks.named(compileTaskName).configure { compileTask ->
        linkGenerateProtoTasksToTask(compileTask as SourceTask, srcDirSet)
      }
    } catch (UnknownDomainObjectException ignore) {
      // It is possible for a compile task to not exist yet. For example, if someone applied
      // the proto plugin and then later applies the kotlin plugin.
      project.tasks.configureEach { Task task ->
        if (task.name == compileTaskName) {
          linkGenerateProtoTasksToTask(task as SourceTask, srcDirSet)
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
}
