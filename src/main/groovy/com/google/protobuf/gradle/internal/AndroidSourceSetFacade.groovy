package com.google.protobuf.gradle.internal

import groovy.transform.CompileStatic
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.api.AndroidSourceSet as DeprecatedAndroidSourceSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer

@CompileStatic
interface AndroidSourceSetFacade {

  String getName()

  ExtensionContainer getExtensions()

  @CompileStatic
  class Default implements AndroidSourceSetFacade {
    private final AndroidSourceSet sourceSet

    Default(AndroidSourceSet sourceSet) {
      this.sourceSet = sourceSet
    }

    @Override
    String getName() {
      return sourceSet.name
    }

    @Override
    ExtensionContainer getExtensions() {
      return (sourceSet as ExtensionAware).extensions
    }
  }

  @CompileStatic
  class Deprecated implements AndroidSourceSetFacade {
    private final DeprecatedAndroidSourceSet sourceSet

    Deprecated(DeprecatedAndroidSourceSet sourceSet) {
      this.sourceSet = sourceSet
    }

    @Override
    String getName() {
      return sourceSet.name
    }

    @Override
    ExtensionContainer getExtensions() {
      return (sourceSet as ExtensionAware).extensions
    }
  }

}
