package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.Utils
import com.google.protobuf.gradle.tasks.GenerateProtoTaskCollection
import com.google.protobuf.gradle.tasks.GenerateProtoTaskSpec
import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

@CompileStatic
class DefaultGenerateProtoTaskCollection implements GenerateProtoTaskCollection {
  private final Project project

  DefaultGenerateProtoTaskCollection(Project project) {
    this.project = project
  }

  NamedDomainObjectSet<ProtoVariant> all() {
    return project.extensions.getByType(ProtobufExtension).variants
  }

  @Override
  void all(Action<GenerateProtoTaskSpec> action) {
    all().all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void all(Closure<GenerateProtoTaskSpec> closure) {
    all(ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofSourceSet(String sourceSet) {
    return all()
      .matching { ProtoVariant variant -> !Utils.isAndroidProject(project) && variant.sourceSet == sourceSet }
  }

  @Override
  void ofSourceSet(String sourceSet, Action<GenerateProtoTaskSpec> action) {
    ofSourceSet(sourceSet)
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofSourceSet(String sourceSet, Closure<GenerateProtoTaskSpec> closure) {
    ofSourceSet(sourceSet, ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofFlavor(String flavor) {
    return all()
      .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.flavors.contains(flavor) }
  }

  @Override
  void ofFlavor(String flavor, Action<GenerateProtoTaskSpec> action) {
    ofFlavor(flavor)
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofFlavor(String flavor, Closure<GenerateProtoTaskSpec> closure) {
    ofFlavor(flavor, ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofBuildType(String buildType) {
    return all()
      .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.buildType == buildType }
  }

  @Override
  void ofBuildType(String buildType, Action<GenerateProtoTaskSpec> action) {
    ofBuildType(buildType)
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofBuildType(String buildType, Closure<GenerateProtoTaskSpec> closure) {
    ofBuildType(buildType, ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofVariant(String name) {
    return all()
      .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.name == name }
  }

  @Override
  void ofVariant(String name, Action<GenerateProtoTaskSpec> action) {
    ofVariant(name)
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofVariant(String name, Closure<GenerateProtoTaskSpec> closure) {
    ofVariant(name, ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofNonTest() {
    return all()
      .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && !variant.isTest }
  }

  @Override
  void ofNonTest(Action<GenerateProtoTaskSpec> action) {
    ofNonTest()
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofNonTest(Closure<GenerateProtoTaskSpec> closure) {
    ofNonTest(ConfigureUtil.configureUsing(closure))
  }

  NamedDomainObjectSet<ProtoVariant> ofTest() {
    all()
      .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.isTest }
  }

  @Override
  void ofTest(Action<GenerateProtoTaskSpec> action) {
    ofTest()
      .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
  }

  @Override
  void ofTest(Closure<GenerateProtoTaskSpec> closure) {
    ofTest(ConfigureUtil.configureUsing(closure))
  }
}
