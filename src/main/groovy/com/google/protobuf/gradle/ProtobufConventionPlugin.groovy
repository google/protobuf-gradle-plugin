package com.google.protobuf.gradle

import com.google.protobuf.gradle.internal.ProjectExt
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider

@CompileStatic
class ProtobufConventionPlugin implements Plugin<Project> {

  private Project project
  private ProtobufExtension protobufExtension

  @Override
  void apply(Project project) {
    ProjectExt.checkMinimalGradleVersion(project)
    this.project = project
    this.protobufExtension = project.extensions.create("protobuf", ProtobufExtension, project)

    // Provides the osdetector extension
    project.pluginManager.apply(ProtobufConventionPlugin)

    configureConfigurationDefaults()
    configureTaskDefaults()
  }

  private void configureConfigurationDefaults() {
    this.protobufExtension.sourceSets.all { ProtoSourceSet protoSourceSet ->
      createProtoSourceConfiguration(protoSourceSet)
    }
  }

  /**
   * Creates a 'protobuf' configuration for the given source set. The build author can
   * configure dependencies for it. The extract-protos task of each source set will
   * extract protobuf files from dependencies in this configuration. Extracted dependencies
   * will passed to proto compiler as sources.
   */
  private Configuration createProtoSourceConfiguration(ProtoSourceSet protoSourceSet) {
    Configuration protobufConf = project.configurations.create(protoSourceSet.protobufConfigurationName)
    protobufConf.visible = false
    protobufConf.transitive = true
    return protobufConf
  }

  private void configureTaskDefaults() {
    this.protobufExtension.sourceSets.all { ProtoSourceSet protoSourceSet ->
      setupExtractProtosTask(protoSourceSet)
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
  private Provider<ProtobufExtract> setupExtractProtosTask(final ProtoSourceSet protoSourceSet) {
    return project.tasks.register(protoSourceSet.extractProtosTaskName, ProtobufExtract) { ProtobufExtract task ->
      task.description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
      task.destDir.set(project.file(protoSourceSet.extractedProtosDir))
      task.inputFiles.from(project.configurations.getByName(protoSourceSet.protobufConfigurationName))
    }
  }
}
