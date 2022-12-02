package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoSourceSet
import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory

@CompileStatic
class DefaultProtoVariant implements ProtoVariant {
  private final String name
  private final ProtoSourceSet sources
  private final DirectoryProperty outputDir
  private final NamedDomainObjectContainer<Object> plugins
  private final NamedDomainObjectContainer<Object> builtins

  DefaultProtoVariant(String name, ObjectFactory objects) {
    this.name = name
    this.sources = new DefaultProtoSourceSet("${name}Sources", objects)
    this.outputDir = objects.directoryProperty()
    this.plugins = objects.domainObjectContainer(Object)
    this.builtins = objects.domainObjectContainer(Object)
  }

  @Override
  String getName() {
    return this.name
  }

  @Override
  ProtoSourceSet getSources() {
    return this.sources
  }

  @Override
  DirectoryProperty getOutputDir() {
    return this.outputDir
  }

  @Override
  NamedDomainObjectContainer<Object> getPlugins() {
    return this.plugins
  }

  @Override
  NamedDomainObjectContainer<Object> getBuiltins() {
    return this.builtins
  }
}
