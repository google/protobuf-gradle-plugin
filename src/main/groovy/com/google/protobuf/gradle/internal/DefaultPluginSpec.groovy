package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.PluginSpec
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory

@CompileStatic
@SuppressWarnings("JUnitPublicNonTestMethod") // it is not a test class
class DefaultPluginSpec implements PluginSpec {
  private final ObjectFactory objects
  private final List<String> options = []
  private final String name
  private String outputSubDir

  DefaultPluginSpec(ObjectFactory objects, String name) {
    this.objects = objects
    this.name = name
  }

  DefaultPluginSpec option(String option) {
    options.add(option)
    return this
  }

  List<String> getOptions() {
    return options
  }

  @Override
  String getName() {
    return name
  }

  void setOutputSubDir(String outputSubDir) {
    this.outputSubDir = outputSubDir
  }

  String getOutputSubDir() {
    if (outputSubDir != null) {
      return outputSubDir
    }
    return name
  }
}
