package com.google.protobuf.gradle.plugins

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

/**
 * Unit tests for android related functionality.
 */
class ProtobufAndroidPluginTest extends Specification {

  void "testProjectAndroid should be successfully executed"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.createTestProject(
        'testProject', 'testProjectBase', 'testProject')
    File testProjectAndroidStaging = ProtobufPluginTestHelper.createTestProject(
        'testProjectAndroid', 'testProjectAndroid')
    File testProjectLiteStaging = ProtobufPluginTestHelper.createTestProject(
        'testProjectLite', 'testProjectLite')
    File mainProjectDir = ProtobufPluginTestHelper.createTestProject('testProjectAndroidMain')
    ProtobufPluginTestHelper.initializeSubProjects(
        mainProjectDir, testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)

    // Add android plugin to the test root project so that Gradle can resolve
    // classpath correctly.
    new File(mainProjectDir, "build.gradle") << """
buildscript {
    String androidPluginVersion = System.properties.get("ANDROID_PLUGIN_VERSION") ?: "2.2.0"
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

    when: "build is invoked"
    File localBuildCache = new File(mainProjectDir, ".buildCache")
    if (localBuildCache.exists()) {
        localBuildCache.deleteDir()
    }
    BuildResult result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments(
              "-DANDROID_PLUGIN_VERSION=${androidPluginVersion}",
              // set android build cache to avoid using home directory on travis CI.
              "-Pandroid.buildCacheDir=" + localBuildCache,
              "testProjectAndroid:build",
              "--stacktrace")
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ["2.2.0", "2.2.0", "2.3.0", "2.3.0"]
    gradleVersion << ["2.14.1", "3.0", "4.2", "4.3"]
  }
}
