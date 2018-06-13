package com.google.protobuf.gradle

import static com.google.protobuf.gradle.ProtobufPluginTestHelper.buildAndroidProject

import org.gradle.testkit.runner.BuildResult
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
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       androidPluginVersion,
       gradleVersion,
       "testProjectAndroid:build"
    )

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
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       androidPluginVersion,
       gradleVersion,
       "testProjectAndroid:build"
    )

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }
}
