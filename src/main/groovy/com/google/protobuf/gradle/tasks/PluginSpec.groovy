package com.google.protobuf.gradle.tasks

import groovy.transform.CompileStatic
import org.gradle.api.tasks.Input

/**
 * The container of command-line options for a protoc plugin or a built-in output.
 */
@CompileStatic
@SuppressWarnings("JUnitPublicNonTestMethod") // it is not a test class
interface PluginSpec {

  /**
   * Adds a plugin option.
   */
  PluginSpec option(String option)

  @Input
  List<String> getOptions()

  /**
   * Returns the name of the plugin or builtin.
   */
  @Input
  String getName()

  /**
   * Set the output directory for this plugin,
   * relative to {@link GenerateProtoTask#outputBaseDir}.
   */
  void setOutputSubDir(String outputSubDir)

  /**
   * Returns the relative outputDir for this plugin.
   * If outputDir is not specified, name is used.
   */
  @Input
  String getOutputSubDir()
}
