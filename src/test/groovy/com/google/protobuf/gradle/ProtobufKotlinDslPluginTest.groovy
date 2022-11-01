package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for kotlin dsl extensions.
 */
@CompileDynamic
class ProtobufKotlinDslPluginTest extends Specification {
  private static final List<String> GRADLE_VERSIONS = ["5.6", "6.1.1", "6.5.1", "7.4.2"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["3.5.0", "4.0.0", "4.1.0", "7.2.1"]

  @Unroll
  void "testProjectKotlinDsl should be successfully executed (java-only project) [gradle #gradleVersion]"() {
    given: "project from testProjectKotlinDslBase"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectKotlinDsl')
        .copyDirs('testProjectKotlinDslBase')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build', '--stacktrace')
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
    // Enabling debug causes the test to fail.
    // https://github.com/gradle/gradle/issues/6862
    //.withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    ProtobufPluginTestHelper.verifyProjectDir(projectDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  @Unroll
  void "testProjectAndroidKotlinDsl should be successfully executed [android #agpVersion, gradle #gradleVersion]"() {
    given: "project from testProjectKotlinDsl"
    File testProjectAndroidKotlinDslStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidKotlinDsl')
            .copyDirs('testProjectAndroidKotlinDsl')
            .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
            .copyDirs('testProjectLite')
            .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidDslMain')
            .copySubProjects(testProjectAndroidKotlinDslStaging, testProjectLiteStaging)
            .withAndroidPlugin(agpVersion)
            .build()

    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
        mainProjectDir,
        gradleVersion,
        agpVersion,
        "testProjectAndroidKotlinDsl:build", //"--scan"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroidKotlinDsl:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSIONS
  }
}
