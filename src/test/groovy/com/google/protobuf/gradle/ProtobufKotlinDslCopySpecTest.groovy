package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit test confirming copy spec is explicitly defined for gradle7+ compliance.
 */
@CompileDynamic
class ProtobufKotlinDslCopySpecTest extends Specification {
  private static final List<String> GRADLE_VERSIONS = ["5.6", "6.0", "6.7.1", "7.0", "7.4.2"]

  @Unroll
  void "testProjectKotlinDslCopySpec should declare explicit copy spec [gradle #gradleVersion]"() {
    given: "project from testProjectKotlinDslCopySpec"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectKotlinDslCopySpec')
            .copyDirs('testProjectKotlinDslCopySpec')
            .build()

    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getGradleRunner(
      projectDir,
      gradleVersion,
      "test",
      "build"
    ).build()

    then: "it succeed"

    result.task(":test").outcome == TaskOutcome.SUCCESS

    verifyProjectDir(projectDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  private static void verifyProjectDir(File projectDir) {
    File generatedSrcDir = new File(projectDir.path, "build/generated/sources/proto/main/java")
    List<File> fileList = []
    generatedSrcDir.eachFileRecurse { file ->
      if (file.path.endsWith('.java')) {
        fileList.add (file)
      }
    }
    assert fileList.size > 0
  }

}
