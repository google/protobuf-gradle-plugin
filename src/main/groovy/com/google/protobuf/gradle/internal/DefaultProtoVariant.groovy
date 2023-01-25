package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.GenerateProtoTaskSpec
import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory

@CompileStatic
class DefaultProtoVariant implements ProtoVariant {
  private final GenerateProtoTaskSpec generateProtoTaskSpec
  private final String name
  private String sourceSet
  private String buildType
  private boolean isTest
  private Set<String> flavors

  DefaultProtoVariant(String name, ObjectFactory objects) {
    this.name = name
    this.generateProtoTaskSpec = new DefaultGenerateProtoTaskSpec(objects)
    this.flavors = [] as Set<String>
  }

  @Override
  GenerateProtoTaskSpec getGenerateProtoTaskSpec() {
    return this.generateProtoTaskSpec
  }

  @Override
  String getSourceSet() {
    return this.sourceSet
  }

  @Override
  void setSourceSet(String sourceSet) {
    this.sourceSet = sourceSet
  }

  @Override
  String getName() {
    return this.name
  }

  @Override
  Set<String> getFlavors() {
    return this.flavors
  }

  @Override
  void setFlavors(Set<String> flavors) {
    this.flavors = flavors
  }

  @Override
  String getBuildType() {
    return this.buildType
  }

  @Override
  void setBuildType(String buildType) {
    this.buildType = buildType
  }

  @Override
  boolean getIsTest() {
    return this.isTest
  }

  @Override
  void setIsTest(boolean isTest) {
    this.isTest = isTest
  }
}
