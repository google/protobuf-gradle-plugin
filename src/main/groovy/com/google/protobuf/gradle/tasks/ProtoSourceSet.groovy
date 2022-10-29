package com.google.protobuf.gradle.tasks

import groovy.transform.CompileStatic
import org.gradle.api.file.SourceDirectorySet

@CompileStatic
interface ProtoSourceSet {
  String getName()

  SourceDirectorySet getProto()

  SourceDirectorySet getIncludeProtoDirs()

  SourceDirectorySet getOutput()

  String getConfigurationName(String configurationName)

  /**
   * @returns task name according to the scheme: ${action}${sourceSetName}${target}
   */
  String getTaskName(String action, String target)
}
