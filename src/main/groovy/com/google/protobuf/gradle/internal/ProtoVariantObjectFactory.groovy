package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory

@CompileStatic
class ProtoVariantObjectFactory implements NamedDomainObjectFactory<ProtoVariant> {
  private final ObjectFactory objects

  ProtoVariantObjectFactory(ObjectFactory objects) {
    this.objects = objects
  }

  @Override
  ProtoVariant create(String name) {
    return new DefaultProtoVariant(name, objects)
  }
}
