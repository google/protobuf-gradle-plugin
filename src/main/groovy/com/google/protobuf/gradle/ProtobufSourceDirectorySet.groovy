package com.google.protobuf.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.ConfigureUtil

public class ProtobufSourceDirectorySet extends DefaultSourceDirectorySet {
  private final NamedDomainObjectContainer<PluginOptions> builtins
  private final NamedDomainObjectContainer<PluginOptions> plugins

  public ProtobufSourceDirectorySet(Project project, String name, FileResolver fileResolver) {
    super(name, String.format("%s Proto source", name), fileResolver)
    builtins = project.container(PluginOptions)
    builtins.create('java')
    plugins = project.container(PluginOptions)
  }

  /**
   * Adds a built-in output and configure its options.
   *
   * <p>Each built-in will be transformed into {@ocde '--<name>_out=[<options>:]<generatedFileDir>'}
   * in protoc command line.
   */
  public ProtobufSourceDirectorySet builtins(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, builtins)
    return this
  }

  /**
   * Adds and configures protoc plugins.
   *
   * <p>Each plugin will be transformed into {@code '--plugin=protoc-gen-<name>=<path>'} and
   * {@code '--<name>_out=[<options>:]<generatedFileDir>'} in protoc command line.
   *
   * <p>The locations of the plugins are defined in {@code protobufCodeGenPlugins} and
   * {@code protobufNativeCodeGenPluginDeps}. If the location of the plugin as not been defined,
   * <code>'${project.projectDir}/protoc-gen-${name}'</code> will be used.
   */
  public ProtobufSourceDirectorySet plugins(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, plugins)
    return this
  }

  public Set<PluginOptions> getPlugins() {
    return plugins
  }

  public Set<PluginOptions> getBuiltins() {
    return builtins
  }

  public static class PluginOptions implements Named {
    private final ArrayList<String> options = new ArrayList<String>()
    private final String name

    public PluginOptions(String name) {
      this.name = name
    }

    /**
     * Adds a plugin option.
     */
    public PluginOptions option(String option) {
      options.add(option)
      return this
    }

    public List<String> getOptions() {
      return options
    }

    public String getName() {
      return name
    }
  }
}
