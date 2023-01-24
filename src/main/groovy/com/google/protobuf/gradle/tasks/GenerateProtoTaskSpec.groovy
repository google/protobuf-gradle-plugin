package com.google.protobuf.gradle.tasks

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

@CompileStatic
@SuppressWarnings("JUnitPublicNonTestMethod") // it is not a test class
interface GenerateProtoTaskSpec {

  /**
   * If true, will set the protoc flag
   * --descriptor_set_out="${outputBaseDir}/descriptor_set.desc"
   *
   * Default: false
   */
  @Input
  boolean getGenerateDescriptorSet()

  void setGenerateDescriptorSet(boolean enabled)

  @Nested
  DescriptorSetSpec getDescriptorSetOptions()

  /**
   * Returns the container of protoc plugins.
   */
  @Nested
  NamedDomainObjectContainer<PluginSpec> getPlugins()

  /**
   * Returns the container of protoc builtins.
   */
  @Nested
  NamedDomainObjectContainer<PluginSpec> getBuiltins()

  /**
   * Returns true if the task has a plugin with the given name, false otherwise.
   */
  boolean hasPlugin(String name)

  /**
   * Configures the protoc builtins in a closure, which will be manipulating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  void builtins(Action<NamedDomainObjectContainer<PluginSpec>> configureAction)

  /**
   * Configures the protoc builtins in a closure, which will be manipulating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  void builtins(Closure<NamedDomainObjectContainer<PluginSpec>> closure)

  /**
   * Configures the protoc plugins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  void plugins(Action<NamedDomainObjectContainer<PluginSpec>> configureAction)

  /**
   * Configures the protoc plugins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  void plugins(Closure<NamedDomainObjectContainer<PluginSpec>> closure)
}
