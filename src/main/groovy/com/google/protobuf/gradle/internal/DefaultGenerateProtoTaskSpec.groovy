package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.api.GenerateProtoTaskSpec
import org.gradle.api.NamedDomainObjectContainer

class DefaultGenerateProtoTaskSpec implements GenerateProtoTaskSpec {
  @Override
  DescriptorSetSpec getDescriptorSet() {
    return null
  }

  @Override
  NamedDomainObjectContainer<PluginSpec> getPlugins() {
    return null
  }

  @Override
  NamedDomainObjectContainer<PluginSpec> getBuiltins() {
    return null
  }
}
