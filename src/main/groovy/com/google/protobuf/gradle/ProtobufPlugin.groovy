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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.google.gradle.osdetector.OsDetectorPlugin
import com.google.protobuf.gradle.internal.AndroidSourceSetFacade
import com.google.protobuf.gradle.internal.AndroidVariantExt
import com.google.protobuf.gradle.internal.DefaultProtoSourceSet
import com.google.protobuf.gradle.internal.ProjectExt
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.jvm.tasks.ProcessResources

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
    'com.android.feature',
    'com.android.library',
    'android',
    'android-library',
  ]

  private Project project
  private ProtobufExtension protobufExtension
  private boolean wasApplied = false

  void apply(Project project) {
    ProjectExt.checkMinimalGradleVersion(project)

    this.project = project
    this.protobufExtension = project.extensions.create("protobuf", ProtobufExtension, project)

    // Provides the osdetector extension
    project.pluginManager.apply(OsDetectorPlugin)

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
    Collection<Closure> postConfigure = []

    if (Utils.isAndroidProject(project)) {
      doAndroidApply(postConfigure)
    } else {
      doJvmApply(postConfigure)
    }

    project.afterEvaluate {
      // Execute configuration actions registered by generateProtoTasks {} block
      this.protobufExtension.configureTasks()

      // Execute deferred actions.
      // For example, kotlin-android plugin configuration
      postConfigure.each { it.call() }

      // Disallow user configuration outside the config closures, because the operations just
      // after the doneConfig() loop over the generated outputs and will be out-of-date if
      // plugin output is added after this point.
      this.protobufExtension.generateProtoTasks.all().configureEach { it.doneConfig() }

      // protoc and codegen plugin configuration may change through the protobuf{}
      // block. Only at this point the configuration has been finalized.
      this.protobufExtension.tools.resolve(project)
    }
  }

  // Java projects will extract included protos from a 'compileProtoPath'
  // configuration of each source set.
  private void doJvmApply(Collection<Closure> postConfigure) {
    SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
    sourceSets.configureEach { SourceSet sourceSet ->
      ProtoSourceSet protoSourceSet = protobufExtension.sourceSets.create(sourceSet.name)
      configureSourceSet(sourceSet, protoSourceSet)

      // Sets up a task to extract protos from protobuf dependencies.
      // They are treated as sources and will be compiled.
      Configuration protobufConf = createProtobufConfiguration(protoSourceSet)
      TaskProvider<ProtobufExtract> extractProtosTask = registerExtractProtosTask(
        protoSourceSet.getExtractProtoTaskName(),
        protoSourceSet.name,
        project.providers.provider { protobufConf as FileCollection },
        project.file("${project.buildDir}/extracted-protos/${protoSourceSet.name}")
      )
      protoSourceSet.proto.srcDir(extractProtosTask)

      // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
      // 'resources' of the output of 'main', in which the source protos are placed.  This is
      // nicer than the ad-hoc solution that Android has, because it works for any extended
      // configuration, not just 'testCompile'.
      TaskProvider<ProtobufExtract> extractIncludeProtosTask = registerExtractProtosTask(
        protoSourceSet.getExtractIncludeProtoTaskName(),
        protoSourceSet.name,
        project.providers.provider {
          Configuration conf = createCompileProtoPathConf(protoSourceSet)
          configureProtoPathConfExtendsFromJvm(conf, protoSourceSet)
          configureCompileProtoPathConfAttrsJvm(conf)
          return conf as FileCollection
        },
        project.file("${project.buildDir}/extracted-include-protos/${protoSourceSet.name}")
      )
      protoSourceSet.includeProtoDirs.srcDir(extractIncludeProtosTask)

      TaskProvider<GenerateProtoTask> generateProtoTask = registerGenerateProtoTask(protoSourceSet)
      protoSourceSet.output.srcDir(generateProtoTask.map { GenerateProtoTask task -> task.outputSourceDirectories })

      // TL; DR: Make protos in 'test' sourceSet able to import protos from the 'main'
      // sourceSet.  Sub-configurations, e.g., 'testCompile' that extends 'compile', don't
      // depend on the their super configurations. As a result, 'testCompile' doesn't depend on
      // 'compile' and it cannot get the proto files from 'main' sourceSet through the
      // configuration.
      if (Utils.isTest(sourceSet.name)) {
        protoSourceSet.includesFrom(protobufExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
      }

      project.tasks.named(protoSourceSet.generateProtoTaskName, GenerateProtoTask)
        .configure({ GenerateProtoTask task ->
          task.sourceSet = sourceSet
          task.builtins.maybeCreate("java")
          task.doneInitializing()
        } as Action<GenerateProtoTask>)

      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      addProtoSourcesToJar(sourceSet, protoSourceSet)
      registerProtoSourcesAsIdeSources(sourceSet, protoSourceSet, postConfigure)
    }
  }

  private void configureSourceSet(SourceSet sourceSet, ProtoSourceSet protoSourceSet) {
    sourceSet.extensions.add("proto", protoSourceSet.proto)
    protoSourceSet.proto.srcDir("src/${protoSourceSet.name}/proto")
    protoSourceSet.proto.include("**/*.proto")

    // Add compiled proto files to java/kotlin sources
    sourceSet.java.srcDirs(protoSourceSet.output)
  }

  /**
   * Creates a 'protobuf' configuration for the given source set. The build author can
   * configure dependencies for it. The extract-protos task of each source set will
   * extract protobuf files from dependencies in this configuration.
   */
  private Configuration createProtobufConfiguration(ProtoSourceSet protoSourceSet) {
    String confName = protoSourceSet.getProtobufConfigurationName()
    Configuration conf = project.configurations.create(confName)

    conf.visible = false
    conf.transitive = true
    conf.canBeConsumed = false
    conf.canBeResolved = true

    return conf
  }

  // Creates an internal 'compileProtoPath' configuration for the given source set that extends
  // compilation configurations as a bucket of dependencies with resources attribute.
  // The extract-include-protos task of each source set will extract protobuf files from
  // resolved dependencies in this configuration.
  //
  // <p> For Java projects only.
  // <p> This works around 'java-library' plugin not exposing resources to consumers for compilation.
  private Configuration createCompileProtoPathConf(ProtoSourceSet protoSourceSet) {
    String confName = protoSourceSet.getCompileProtoPathConfigurationName()
    Configuration conf = project.configurations.create(confName)

    conf.visible = false
    conf.transitive = true
    conf.canBeConsumed = false
    conf.canBeResolved = true

    return conf
  }

  private void configureProtoPathConfExtendsFromJvm(Configuration conf, ProtoSourceSet protoSourceSet) {
    String compileOnlyConfName = protoSourceSet.getCompileOnlyConfigurationName()
    Configuration compileOnlyConf = project.configurations.getByName(compileOnlyConfName)
    String implementationConfName = protoSourceSet.getImplementationConfigurationName()
    Configuration implementationConf = project.configurations.getByName(implementationConfName)
    conf.extendsFrom(compileOnlyConf, implementationConf)
  }

  private void configureCompileProtoPathConfAttrsJvm(Configuration conf) {
    // Variant attributes are not inherited. Setting it too loosely can
    // result in ambiguous variant selection errors.
    // CompileProtoPath only need proto files from dependency's resources.
    // LibraryElement "resources" is compatible with "jar" (if a "resources" variant is
    // not found, the "jar" variant will be used).
    conf.attributes.attribute(
      LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
      project.getObjects().named(LibraryElements, LibraryElements.RESOURCES)
    )

    // Although variants with any usage has proto files, not setting usage attribute
    // can result in ambiguous variant selection if the producer provides multiple
    // variants with different usage attribute.
    // Preserve the usage attribute from CompileOnly and Implementation.
    conf.attributes.attribute(
      Usage.USAGE_ATTRIBUTE,
      project.getObjects().named(Usage, Usage.JAVA_RUNTIME)
    )
  }

  private TaskProvider<ProtobufExtract> registerExtractProtosTask(
    String taskName,
    String sourceName,
    Provider<FileCollection> extractFrom,
    File outputDir
  ) {
    return project.tasks.register(taskName, ProtobufExtract) { ProtobufExtract task ->
      FileCollection conf = extractFrom.get()
      task.description = "Extracts proto files/dependencies specified by '$sourceName' source"
      task.destDir.set(outputDir)
      task.inputFiles.from(conf)
    }
  }

  private TaskProvider<GenerateProtoTask> registerGenerateProtoTask(ProtoSourceSet protoSourceSet) {
    String taskName = protoSourceSet.getGenerateProtoTaskName()
    return project.tasks.register(taskName, GenerateProtoTask) { GenerateProtoTask task ->
      task.description = "Compiles Proto source for '${protoSourceSet.name}'"
      task.outputBaseDir = project.providers.provider {
        "${this.protobufExtension.generatedFilesBaseDir}/${protoSourceSet.name}".toString()
      }
      task.addSourceDirs(protoSourceSet.proto)
      task.addIncludeDir(protoSourceSet.proto.sourceDirectories)
      task.addIncludeDir(protoSourceSet.includeProtoDirs.sourceDirectories)
    }
  }

  private void addProtoSourcesToJar(SourceSet sourceSet, ProtoSourceSet protoSourceSet) {
    String processResourcesTaskName = sourceSet.getTaskName('process', 'resources')
    project.tasks.named(processResourcesTaskName, ProcessResources) { ProcessResources task ->
      task.from(protoSourceSet.proto) { CopySpec cs ->
        cs.include '**/*.proto'
      }
    }
  }

  private void registerProtoSourcesAsIdeSources(
    SourceSet sourceSet,
    ProtoSourceSet protoSourceSet,
    Collection<Closure> postConfigure
  ) {
    postConfigure.add {
      project.plugins.withId("eclipse") {
        // This is required because the intellij/eclipse plugin does not allow adding source directories
        // that do not exist. The intellij/eclipse config files should be valid from the start.
        protoSourceSet.output.sourceDirectories.each { File outputDir ->
          outputDir.mkdirs()
        }
      }

      project.plugins.withId("idea") {
        boolean isTest = Utils.isTest(sourceSet.name)
        protoSourceSet.proto.srcDirs.each { File protoDir ->
          Utils.addToIdeSources(
            project,
            isTest,
            protoDir,
            protoDir.absolutePath.replace(project.rootDir.absolutePath, "").contains("build")
          )
        }
        protoSourceSet.includeProtoDirs.srcDirs.each { File protoDir ->
          Utils.addToIdeSources(
            project,
            isTest,
            protoDir,
            protoDir.absolutePath.replace(project.rootDir.absolutePath, "").contains("build")
          )
        }
        protoSourceSet.output.srcDirs.each { File outputDir ->
          Utils.addToIdeSources(
            project,
            isTest,
            outputDir,
            outputDir.absolutePath.replace(project.rootDir.absolutePath, "").contains("build")
          )
        }
      }
    }
  }

  // Android projects will extract included protos from {@code variant.compileConfiguration}
  // of each variant.
  private void doAndroidApply(Collection<Closure> postConfigure) {
    BaseExtension androidExtension = project.extensions.getByType(BaseExtension)

    androidExtension.sourceSets.configureEach { Object sourceSet ->
      AndroidSourceSetFacade sourceSetFacade = new AndroidSourceSetFacade(sourceSet)
      ProtoSourceSet protoSourceSet = protobufExtension.sourceSets.create(sourceSetFacade.name)
      configureSourceSet(sourceSetFacade, protoSourceSet)

      // Sets up a task to extract protos from protobuf dependencies.
      // They are treated as sources and will be compiled.
      Configuration protobufConf = createProtobufConfiguration(protoSourceSet)
      TaskProvider<ProtobufExtract> extractProtosTask = registerExtractProtosTask(
        protoSourceSet.getExtractProtoTaskName(),
        protoSourceSet.name,
        project.providers.provider { protobufConf as FileCollection },
        project.file("${project.buildDir}/extracted-protos/${protoSourceSet.name}")
      )
      protoSourceSet.proto.srcDir(extractProtosTask)
    }

    NamedDomainObjectContainer<ProtoSourceSet> variantMergedSourceSets =
      project.objects.domainObjectContainer(ProtoSourceSet) { String name ->
        new DefaultProtoSourceSet(name, project.objects) as ProtoSourceSet
      }
    ProjectExt.allVariant(project) { BaseVariant variant ->
      ProtoSourceSet variantProtoSourceSet = variantMergedSourceSets.create(variant.name)
      variant.sourceSets.each { SourceProvider sourceProvider ->
        variantProtoSourceSet.extendsFrom(protobufExtension.sourceSets.getByName(sourceProvider.name))
      }

      TaskProvider<ProtobufExtract> extractIncludeProtosTask = registerExtractProtosTask(
        variantProtoSourceSet.getExtractIncludeProtoTaskName(),
        variantProtoSourceSet.name,
        project.providers.provider {
          Configuration conf = createCompileProtoPathConf(variantProtoSourceSet)
          configureProtoPathConfExtendsFromAndroid(conf, variant.sourceSets)
          configureCompileProtoPathConfAttrsAndroid(conf, variant)
          return getIncomingJarFromConf(conf)
        },
        project.file("${project.buildDir}/extracted-include-protos/${variantProtoSourceSet.name}")
      )
      variantProtoSourceSet.includeProtoDirs.srcDir(extractIncludeProtosTask)

      TaskProvider<GenerateProtoTask> generateProtoTask = registerGenerateProtoTask(variantProtoSourceSet)
      variantProtoSourceSet.output
        .srcDir(generateProtoTask.map { GenerateProtoTask task -> task.outputSourceDirectories })

      BaseVariant testedVariant = AndroidVariantExt.getTestVariant(variant)
      if (testedVariant != null) {
        postConfigure.add {
          variantProtoSourceSet.includesFrom(protobufExtension.sourceSets.getByName("main"))
          variantProtoSourceSet.includesFrom(variantMergedSourceSets.getByName(testedVariant.name))
        }
      }

      generateProtoTask.configure({ GenerateProtoTask task ->
        task.setVariant(variant, Utils.isTest(variant.name))
        task.setBuildType(variant.buildType.name)
        task.setFlavors(variant.productFlavors.collect { ProductFlavor flavor -> flavor.name })
        task.doneInitializing()
      } as Action<GenerateProtoTask>)

      // Include source proto files in the compiled archive, so that proto files from
      // dependent projects can import them.
      addProtoSourcesToAar(variant, variantProtoSourceSet)

      postConfigure.add {
        variant.registerJavaGeneratingTask(generateProtoTask.get(), generateProtoTask.get().outputSourceDirectories)
        configureAndroidKotlinCompileTasks(variant, variantProtoSourceSet)
      }
    }
  }

  private void configureProtoPathConfExtendsFromAndroid(Configuration conf, Collection<SourceProvider> sourceSets) {
    sourceSets.each { SourceProvider sourceProvider ->
      ProtoSourceSet protoSourceSet = protobufExtension.sourceSets.getByName(sourceProvider.name)

      String compileOnlyConfigurationName = protoSourceSet.getCompileOnlyConfigurationName()
      Configuration compileOnlyConfiguration = project.configurations.getByName(compileOnlyConfigurationName)
      String implementationConfigurationName = protoSourceSet.getImplementationConfigurationName()
      Configuration implementationConfiguration = project.configurations.getByName(implementationConfigurationName)
      conf.extendsFrom(compileOnlyConfiguration, implementationConfiguration)
    }
  }

  private void configureCompileProtoPathConfAttrsAndroid(Configuration conf, BaseVariant variant) {
    AttributeContainer confAttrs = conf.attributes
    variant.compileConfiguration.attributes.keySet().each { Attribute<Object> attr ->
      Object attrValue = variant.compileConfiguration.attributes.getAttribute(attr)
      confAttrs.attribute(attr, attrValue)
    }
  }

  private FileCollection getIncomingJarFromConf(Configuration conf) {
    return conf.incoming.artifactView { ArtifactView.ViewConfiguration viewConf ->
      viewConf.attributes.attribute(
        ArtifactAttributes.ARTIFACT_FORMAT,
        ArtifactTypeDefinition.JAR_TYPE
      )
    }.files
  }

  private void addProtoSourcesToAar(BaseVariant variant, ProtoSourceSet protoSourceSet) {
    variant.getProcessJavaResourcesProvider().configure { ProcessResources task ->
      task.from(protoSourceSet.proto) { CopySpec cs ->
        cs.include('**/*.proto')
      }
    }
  }

  private void configureAndroidKotlinCompileTasks(BaseVariant variant, ProtoSourceSet protoSourceSet) {
    project.plugins.withId("org.jetbrains.kotlin.android") {
      project.afterEvaluate {
        String compileKotlinTaskName = Utils.getKotlinAndroidCompileTaskName(project, variant.name)
        project.tasks.named(compileKotlinTaskName, SourceTask) { SourceTask task ->
          task.source(protoSourceSet.output)
        }
      }
    }
  }

  /**
   * Adds the proto extension to the SourceSet, e.g., it creates
   * sourceSets.main.proto and sourceSets.test.proto.
   */
  private void configureSourceSet(AndroidSourceSetFacade sourceSetFacade, ProtoSourceSet protoSourceSet) {
    sourceSetFacade.extensions.add("proto", protoSourceSet.proto)
    protoSourceSet.proto.srcDir("src/${protoSourceSet.name}/proto")
    protoSourceSet.proto.include("**/*.proto")
  }
}
