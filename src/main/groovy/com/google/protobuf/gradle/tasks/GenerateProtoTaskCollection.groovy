package com.google.protobuf.gradle.tasks

import org.gradle.api.Action

interface GenerateProtoTaskCollection {
  void all(Action<GenerateProtoTaskSpec> action)

  void all(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofSourceSet(String sourceSet, Action<GenerateProtoTaskSpec> action)

  void ofSourceSet(String sourceSet, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofFlavor(String flavor, Action<GenerateProtoTaskSpec> action)

  void ofFlavor(String flavor, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofBuildType(String buildType, Action<GenerateProtoTaskSpec> action)

  void ofBuildType(String buildType, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofVariant(String name, Action<GenerateProtoTaskSpec> action)

  void ofVariant(String name, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofNonTest(Action<GenerateProtoTaskSpec> action)

  void ofNonTest(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)

  void ofTest(Action<GenerateProtoTaskSpec> action)

  void ofTest(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure)
}
