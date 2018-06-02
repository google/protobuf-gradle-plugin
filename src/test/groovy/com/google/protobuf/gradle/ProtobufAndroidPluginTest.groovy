package com.google.protobuf.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

/**
 * Unit tests for android related functionality.
 */
class ProtobufAndroidPluginTest extends Specification {
  private static final List<String> GRADLE_VERSION = ["2.14.1", "3.0", "4.2", "4.3"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["2.2.0", "2.2.0", "2.3.0", "2.3.0"]

  void "testProjectAndroid should be successfully executed (java only)"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
        .copyDirs('testProjectBase', 'testProject')
        .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
        .copyDirs('testProjectAndroidBase', 'testProjectAndroid')
        .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
        .copyDirs('testProjectLite')
        .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidMain')
        .copySubProjects(testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)
        .build()
    when: "build is invoked"
    BuildResult result = testHelper(mainProjectDir, androidPluginVersion, gradleVersion)

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  void "testProjectAndroidKotlin should be successfully executed (kotlin only)"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
        .copyDirs('testProjectBase', 'testProject')
        .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
        .copyDirs('testProjectAndroidBase', 'testProjectAndroidKotlin')
        .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
        .copyDirs('testProjectLite')
        .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidMain')
        .copySubProjects(testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)
        .build()
    when: "build is invoked"
    BuildResult result = testHelper(mainProjectDir, androidPluginVersion, gradleVersion)

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  private static BuildResult testHelper(
      File mainProjectDir, String androidPluginVersion, String gradleVersion) {
    // Add android plugin to the test root project so that Gradle can resolve
    // classpath correctly.
    new File(mainProjectDir, "build.gradle") << """
buildscript {
    String androidPluginVersion = "${androidPluginVersion}"
    repositories {
        maven { url "https://plugins.gradle.org/m2/" }
        if (androidPluginVersion.startsWith("3.")) {
            google()
        }
    }
    dependencies {
        classpath "com.android.tools.build:gradle:\$androidPluginVersion"
    }
}
"""

    File localBuildCache = new File(mainProjectDir, ".buildCache")
    if (localBuildCache.exists()) {
      localBuildCache.deleteDir()
    }
    return GradleRunner.create()
        .withProjectDir(mainProjectDir)
        .withArguments(
        "-DANDROID_PLUGIN_VERSION=${androidPluginVersion}",
        // set android build cache to avoid using home directory on travis CI.
        "-Pandroid.buildCacheDir=" + localBuildCache,
        "testProjectAndroid:build",
        "-x", "lint", // linter causes .withDebug(true) to fail
        "--stacktrace")
        .withGradleVersion(gradleVersion)
        .forwardStdOutput(new OutputStreamWriter(System.out))
        .forwardStdError(new OutputStreamWriter(System.err))
        .withDebug(true)
        .build()
  }
}
