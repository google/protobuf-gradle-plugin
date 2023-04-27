package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.DescriptorSetSpec
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory

@CompileStatic
@SuppressWarnings("JUnitPublicNonTestMethod") // it is not a test class
class DefaultDescriptorSetSpec implements DescriptorSetSpec {
  private final ObjectFactory objects
  private String path
  private boolean includeSourceInfo
  private boolean includeImports

  DefaultDescriptorSetSpec(ObjectFactory objects) {
    this.objects = objects
    this.path = null
    this.includeSourceInfo = false
    this.includeImports = false
  }

  @Override
  String getPath() {
    return this.path
  }

  @Override
  void setPath(String value) {
    this.path = value
  }

  @Override
  boolean getIncludeSourceInfo() {
    return this.includeSourceInfo
  }

  @Override
  void setIncludeSourceInfo(boolean value) {
    this.includeSourceInfo = value
  }

  @Override
  boolean getIncludeImports() {
    return this.includeImports
  }

  @Override
  void setIncludeImports(boolean value) {
    this.includeImports = value
  }
}
