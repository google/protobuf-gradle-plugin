package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory

@CompileStatic
class ProtoSourceSetObjectFactory implements NamedDomainObjectFactory<ProtoSourceSet> {
  private final ObjectFactory objects

  ProtoSourceSetObjectFactory(ObjectFactory objects) {
    this.objects = objects
  }

  @Override
  ProtoSourceSet create(String name) {
    return new DefaultProtoSourceSet(name, objects)
  }
}
