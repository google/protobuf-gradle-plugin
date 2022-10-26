package com.google.protobuf.gradle

import groovy.transform.CompileStatic
import org.gradle.api.file.SourceDirectorySet

@CompileStatic
interface ProtoSourceSet {
  String getName()

  SourceDirectorySet getProto()

  String getConfigurationNameOf(String configurationName)
}
