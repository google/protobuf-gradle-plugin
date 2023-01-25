package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project

@CompileStatic
class ProtoVariantObjectFactory implements NamedDomainObjectFactory<ProtoVariant> {
  private final Project project

  ProtoVariantObjectFactory(Project project) {
    this.project = project
  }

  @Override
  ProtoVariant create(String name) {
    return new DefaultProtoVariant(name, project)
  }
}
