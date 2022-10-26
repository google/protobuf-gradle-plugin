package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.ProtoSourceSet
import com.google.protobuf.gradle.Utils
import groovy.transform.CompileStatic
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet

@CompileStatic
class DefaultProtoSourceSet implements ProtoSourceSet {
  private final String name
  private final String displayName
  private final SourceDirectorySet proto

  public DefaultProtoSourceSet(String name, String displayName, ObjectFactory objectFactory) {
    this.name = name
    this.displayName = displayName
    this.proto = objectFactory.sourceDirectorySet(name, "$displayName Proto Source")
    this.proto.filter.include("**/*.proto")
  }

  @Override
  String getName() {
    return this.name
  }

  @Override
  SourceDirectorySet getProto() {
    return this.proto
  }

  @Override
  @SuppressWarnings(["SpaceAroundOperator"])
  String getConfigurationNameOf(String configurationName) {
    return this.name == SourceSet.MAIN_SOURCE_SET_NAME
      ? configurationName
      : "${this.name}${configurationName.capitalize()}"
  }

  @Override
  String getProtobufConfigurationName() {
    return this.getConfigurationNameOf("protobuf")
  }

  @Override
  String getExtractProtosTaskName() {
    return "extract${Utils.getSourceSetSubstringForTaskNames(this.name)}Proto"
  }

  @Override
  String getExtractedProtosDir() {
    return "build/extracted-protos/${this.name}"
  }
}
