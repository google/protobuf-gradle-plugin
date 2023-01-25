package com.google.protobuf.gradle.tasks

import groovy.transform.CompileStatic

@CompileStatic
interface ProtoVariant {
  GenerateProtoTaskSpec getGenerateProtoTaskSpec()

  String getSourceSet()

  void setSourceSet(String name)

  String getName()

  Set<String> getFlavors()

  void setFlavors(Set<String> flavors)

  String getBuildType()

  void setBuildType(String name)

  boolean getIsTest()

  void setIsTest(boolean value)
}
