package com.google.protobuf.gradle.internal

import groovy.transform.CompileDynamic
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.api.AndroidSourceSet as DeprecatedAndroidSourceSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer

@CompileDynamic
class AndroidSourceSetFacade {
  private final Object sourceSet

  AndroidSourceSetFacade(Object sourceSet) {
    this.sourceSet = sourceSet
    if (sourceSet instanceof DeprecatedAndroidSourceSet) {
      return
    }
    if (sourceSet instanceof AndroidSourceSet) {
      return
    }
    throw new IllegalArgumentException("sourceSet param should be 'com.android.build.api.dsl.AndroidSourceSet' " +
      "or 'com.android.build.gradle.api.AndroidSourceSet', but '${sourceSet.class.packageName}' was present")
  }

  String getName() {
    return this.sourceSet.name
  }

  ExtensionContainer getExtensions() {
    return (this.sourceSet as ExtensionAware).extensions
  }

}
