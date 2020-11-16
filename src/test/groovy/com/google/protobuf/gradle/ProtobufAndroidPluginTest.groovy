package com.google.protobuf.gradle

import static com.google.protobuf.gradle.ProtobufPluginTestHelper.buildAndroidProject
import static com.google.protobuf.gradle.ProtobufPluginTestHelper.getAndroidGradleRunner

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for android related functionality.
 */
@CompileDynamic
class ProtobufAndroidPluginTest extends Specification {
  private static final List<String> GRADLE_VERSION = ["5.6", "6.5-milestone-1"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["3.5.0", "4.1.0-alpha10"]
  private static final List<String> KOTLIN_VERSION = ["1.3.20"]

  @Unroll
  void "testProjectAndroid should be successfully executed [android #agpVersion, gradle #gradleVersion]"() {
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
        .withAndroidPlugin(agpVersion)
        .build()
    when: "build is invoked"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       gradleVersion,
       "testProjectAndroid:build"
    )

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  @Unroll
  void "testProjectAndroid succeeds with configuration cache [android #agpVersion, gradle #gradleVersion]"() {
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
            .withAndroidPlugin(agpVersion)
            .build()
    and:
    GradleRunner runner = getAndroidGradleRunner(
            mainProjectDir,
            gradleVersion,
            "testProjectAndroid:assembleDebug",
            "-Dorg.gradle.unsafe.instant-execution=true",
            "-Dorg.gradle.unsafe.instant-execution.fail-on-problems=false"
    )
    when: "build is invoked"
    BuildResult result = runner.build()

    then: "it caches the task graph"
    result.output.contains("Calculating task graph")

    and: "it succeed"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.SUCCESS

    when: "build is invoked again"
    result = runner.build()

    then: "it reuses the task graph"
    result.output.contains("Reusing instant execution cache")

    and: "it is up to date"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.UP_TO_DATE

    when: "clean is invoked, before a build"
    buildAndroidProject(
            mainProjectDir,
            gradleVersion,
            "testProjectAndroid:clean",
            "-Dorg.gradle.unsafe.instant-execution=true",
            "-Dorg.gradle.unsafe.instant-execution.fail-on-problems=false"
    )
    result = runner.build()

    then: "it succeed"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION.takeRight(1)
    gradleVersion << GRADLE_VERSION.takeRight(1)
  }

  @Unroll
  void "testProjectAndroidKotlin [android #agpVersion, gradle #gradleVersion, kotlin #kotlinVersion]"() {
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
        .withAndroidPlugin(agpVersion)
        .withKotlin(kotlinVersion)
        .build()
    when: "build is invoked"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       gradleVersion,
       "testProjectAndroid:build"
    )

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
    kotlinVersion << KOTLIN_VERSION + KOTLIN_VERSION
  }
}
