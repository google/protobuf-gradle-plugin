package com.google.protobuf.gradle.tasks

import groovy.transform.CompileStatic
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import javax.annotation.Nullable

/**
 * Configuration object for descriptor generation details.
 */
@CompileStatic
@SuppressWarnings("JUnitPublicNonTestMethod") // it is not a test class
interface DescriptorSetSpec {
  /**
   * If set, specifies an alternative location than the default for storing the descriptor
   * set.
   *
   * Default: null
   */
  @Nullable
  @Optional
  @OutputFile
  String getPath()

  void setPath(String value)

  /**
   * If true, source information (comments, locations) will be included in the descriptor set.
   *
   * Default: false
   */
  @Input
  boolean getIncludeSourceInfo()

  void setIncludeSourceInfo(boolean value)

  /**
   * If true, imports are included in the descriptor set, such that it is self-containing.
   *
   * Default: false
   */
  @Input
  boolean getIncludeImports()

  void setIncludeImports(boolean value)
}
