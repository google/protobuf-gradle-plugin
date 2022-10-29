package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.Nullable

@CompileStatic
class DefaultProtoSourceSet implements ProtoSourceSet {
  private final String name
  private final SourceDirectorySet proto
  private final SourceDirectorySet includeProtoDirs
  private final SourceDirectorySet output

  DefaultProtoSourceSet(String name, ObjectFactory objects) {
    this.name = name
    this.proto = objects.sourceDirectorySet("proto", "${name.capitalize()} Proto Source")
    this.includeProtoDirs = objects.sourceDirectorySet("proto", "${name.capitalize()} Proto Include Dirs")
    this.output = objects.sourceDirectorySet("compiled", "${name.capitalize()} Compiled Proto")
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
  SourceDirectorySet getIncludeProtoDirs() {
    return this.includeProtoDirs
  }

  @Override
  SourceDirectorySet getOutput() {
    return this.output
  }

  @Override
  @SuppressWarnings(["SpaceAroundOperator"])
  String getConfigurationName(String configurationName) {
    return this.name == SourceSet.MAIN_SOURCE_SET_NAME
      ? configurationName
      : "${this.name}${configurationName.capitalize()}"
  }

  @Override
  String getTaskName(@Nullable String action, @Nullable String target) {
    String sourceSetName = this.name == SourceSet.MAIN_SOURCE_SET_NAME ? "" : this.name
    return "${action}${sourceSetName.capitalize()}${target.capitalize()}"
  }
}
