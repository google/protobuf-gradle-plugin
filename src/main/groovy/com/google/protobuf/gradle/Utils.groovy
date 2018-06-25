/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.protobuf.gradle

import com.google.common.base.Preconditions
import org.apache.commons.lang.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskInputs
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.util.GUtil

import java.util.regex.Matcher

/**
 * Utility classes.
 */
class Utils {
  /**
   * Returns the conventional name of a configuration for a sourceSet
   */
  static String getConfigName(String sourceSetName, String type) {
    return sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME ?
        type : (sourceSetName + StringUtils.capitalize(type))
  }

  /**
   * Returns the conventional substring that represents the sourceSet in task names,
   * e.g., "generate<sourceSetSubstring>Proto"
   */
  static String getSourceSetSubstringForTaskNames(String sourceSetName) {
    return sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME ?
        '' : StringUtils.capitalize(sourceSetName)
  }

  private static final String ANDROID_BASE_PLUGIN_ID = "com.android.base"
  private static final List<String> ANDROID_PLUGIN_IDS = [
      'android',
      'android-library',
      'com.android.application',
      'com.android.feature',
      'com.android.instantapp',
      'com.android.library',
      'com.android.test',
  ]

  /**
   * Detects if an android plugin has been applied to the project
   */
  static boolean isAndroidProject(Project project) {
    // Projects are marked with com.android.base plugin from version 3.0.0 up
    // OR fall back to a list of plugin id's to support versions prior to 3.0.0
    return project.plugins.hasPlugin(ANDROID_BASE_PLUGIN_ID) ||
        ANDROID_PLUGIN_IDS.any { String pluginId ->
          project.plugins.hasPlugin(pluginId)
        }
  }

  /**
   * Returns the compile task name for Kotlin.
   */
  static String getKotlinAndroidCompileTaskName(Project project, String variantName) {
    // The kotlin plugin does not provide a utility for this.
    // Fortunately, the naming scheme is well defined:
    // https://kotlinlang.org/docs/reference/using-gradle.html#compiler-options
    Preconditions.checkState(isAndroidProject(project))
    return "compile" + GUtil.toCamelCase(variantName) + "Kotlin"
  }

  static void addFilesToTaskInputs(Project project, TaskInputs inputs, Object files) {
    if (compareGradleVersion(project, "3.0") >= 0) {
      inputs.files(files).skipWhenEmpty()
    } else {
      // source() is deprecated since Gradle 3.0
      inputs.source(files)
    }
  }

  /**
   * Returns positive/0/negative if current Gradle version is higher than/equal to/lower than the
   * given target version.  Only major and minor versions are checked.  Patch version is ignored.
   */
  static int compareGradleVersion(Project project, String target) {
    Matcher gv = parseVersionString(project.gradle.gradleVersion)
    Matcher tv = parseVersionString(target)
    int majorVersionDiff = gv.group(1).toInteger() - tv.group(1).toInteger()
    if (majorVersionDiff != 0) {
      return majorVersionDiff
    }
    return gv.group(2).toInteger() - tv.group(2).toInteger()
  }

  private static Matcher parseVersionString(String version) {
    Matcher matcher = version =~ "(\\d*)\\.(\\d*).*"
    if (!matcher || !matcher.matches()) {
      throw new GradleException("Failed to parse version \"${version}\"")
    }
    return matcher
  }

  /**
   * Returns true if the source set is a test related source set.
   */
  static boolean isTest(String sourceSetOrVariantName) {
    return sourceSetOrVariantName == "test" ||
        sourceSetOrVariantName.toLowerCase().contains('androidtest') ||
        sourceSetOrVariantName.toLowerCase().contains('unittest')
  }

  /**
   * Adds the file to the IDE plugin's set of sources / resources. If the directory does
   * not exist, it will be created before the IDE task is run.
   */
  static void addToIdeSources(Project project, boolean isTest, File f) {
    IdeaModel model = project.getExtensions().findByType(IdeaModel)
    if (model != null) {
      // TODO(zpencer): switch to model.module.generatedSourceDirs when that API becomes stable
      // For now, just hint to the IDE that it's a source dir or a test source dir.
      if (isTest) {
        model.module.testSourceDirs += f
      } else {
        model.module.sourceDirs += f
      }
      project.tasks.withType(GenerateIdeaModule).each {
        it.doFirst {
          // This is required because the intellij plugin does not allow adding source directories
          // that do not exist. The intellij config files should be valid from the start even if a
          // user runs './gradlew idea' before running './gradlew generateProto'.
          f.mkdirs()
        }
      }
    }
  }
}
