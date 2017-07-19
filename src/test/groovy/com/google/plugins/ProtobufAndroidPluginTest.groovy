package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class ProtobufAndroidPluginTest extends Specification {

  def "testProjectAndroid should be successfully executed"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    def mainProjectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectAndroid')
    ProtobufPluginTestHelper.copyTestProjects(mainProjectDir, 'testProject', 'testProjectLite', 'testProjectAndroid')

    // Add android plugin to the test root project so that Gradle can resolve
    // classpath correctly.
    new File(mainProjectDir, "build.gradle") << """
buildscript {
    def androidPluginVersion = System.properties.get("ANDROID_PLUGIN_VERSION") ?: "2.2.0"
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

    new File(mainProjectDir, "gradle.properties") << """org.gradle.jvmargs=-Xmx1536m"""


    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments(
              "-DANDROID_PLUGIN_VERSION=${androidPluginVersion}", 
              "-Pandroid.buildCacheDir=" + new File(mainProjectDir, ".buildCache"), // set android build cache to avoid using home directory on travis CI.
              "testProjectAndroid:build",
              "--stacktrace")
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ["2.2.0", "2.2.0", "3.0.0-alpha6"]
    gradleVersion << ["2.14.1", "3.0", "4.1-milestone-1"]
  }
}
