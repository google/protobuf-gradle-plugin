package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.PluginSpec
import groovy.transform.CompileStatic

@CompileStatic
class PluginSpecExt {
  private PluginSpecExt() {
  }

  static String getOutputDir(PluginSpec plugin, String outputBaseDir) {
    return "${outputBaseDir}/${plugin.outputSubDir}"
  }
}
