package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.GenerateProtoTaskSpec
import groovy.transform.CompileStatic

@CompileStatic
class GenerateProtoTaskSpecExt {
  private GenerateProtoTaskSpecExt() {
  }

  static Collection<File> getOutputSourceDirectories(GenerateProtoTaskSpec spec) {
    Collection<File> srcDirs = []

    spec.builtins.each { builtin ->
      File dir = new File(PluginSpecExt.getOutputDir(builtin, spec.outputDir.get()))
      if (!dir.name.endsWith(".zip") && !dir.name.endsWith(".jar")) {
        srcDirs.add(dir)
      }
    }
    spec.plugins.each { plugin ->
      File dir = new File(PluginSpecExt.getOutputDir(plugin, spec.outputDir.get()))
      if (!dir.name.endsWith(".zip") && !dir.name.endsWith(".jar")) {
        srcDirs.add(dir)
      }
    }

    return srcDirs
  }
}
