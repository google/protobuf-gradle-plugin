package com.google.protobuf.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for kotlin dsl extensions.
 */
class ProtobufKotlinDslPluginTest extends Specification {
  // Current supported version is Gradle 5+.
  private static final List<String> GRADLE_VERSIONS = ["5.0", "5.1", "5.4", "5.6", "6.0"]

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
}
