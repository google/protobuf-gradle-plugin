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

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('testProjectAndroid:build')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << ["2.14.1", "3.0"]
  }
}
