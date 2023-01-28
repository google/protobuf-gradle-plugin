package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.PluginSpec
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory

@CompileStatic
class PluginSpecObjectFactory implements NamedDomainObjectFactory<PluginSpec> {
  private final ObjectFactory objects

  PluginSpecObjectFactory(ObjectFactory objects) {
    this.objects = objects
  }

  @Override
  PluginSpec create(String name) {
    return new DefaultPluginSpec(objects, name)
  }
}
