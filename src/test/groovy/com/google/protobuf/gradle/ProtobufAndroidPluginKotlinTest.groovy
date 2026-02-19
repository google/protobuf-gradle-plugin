package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

@CompileDynamic
class ProtobufAndroidPluginKotlinTest extends Specification {
  private static final List<String> GRADLE_VERSION = ["7.6.2", "8.7", "8.9", "8.13"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["7.4.2", "8.5.0", "8.7.0", "8.13.0"]
  private static final List<String> KOTLIN_VERSION = ["1.7.20", "1.7.20", "1.8.20", "1.9.20"]

  /**
   * This test may take a significant amount of Gradle daemon Metaspace memory in some
   * Gradle + AGP versions. Try running it separately
   */
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
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
        mainProjectDir,
        gradleVersion,
        agpVersion,
        "testProjectAndroid:build"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
    kotlinVersion << KOTLIN_VERSION
    }
}
