package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

@CompileDynamic
class ProtobufAndroidPluginKotlinTest extends Specification {

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

    ArrayList<String> androidSources = ['testProjectAndroidBase', 'testProjectAndroidKotlin']
    if (agpVersion.startsWith("9.")) {
        androidSources += 'testProjectAndroidBase9'
    }
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
            .copyDirs(*androidSources)
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
    agpVersion | gradleVersion | kotlinVersion
    "7.4.2"    | "7.6.2"       | "1.7.20"
    "8.5.0"    | "8.7"         | "1.7.20"
    "8.7.0"    | "8.9"         | "1.8.20"
    "8.13.0"   | "8.13"        | "1.9.20"
    "9.0.1"    | "9.1.0"       | "2.2.20"
    "9.1.0-alpha05"    | "9.3.1"       | "2.3.0"
  }
}
