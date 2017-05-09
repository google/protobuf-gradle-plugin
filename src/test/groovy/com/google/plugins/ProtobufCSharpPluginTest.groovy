package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProtobufCSharpPluginTest extends Specification {

  @Rule
  final TemporaryFolder tempDir = new TemporaryFolder()

  def "testProjectCSharp should be successfully executed"() {
    given: "project from testProjectCSharp"
    def mainProjectDir = tempDir.newFolder('test')
    ProtobufPluginTestHelper.copyTestProject(mainProjectDir, 'testProjectCSharp')

    when: "msbuild is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('msbuild', '--stacktrace')
      .build()

    then: "it succeed"
    result.task(":msbuild").outcome == TaskOutcome.SUCCESS
  }

  def "testProjectCSharpAndJava should be successfully executed"() {
    given: "project from testProjectCSharpAndJava"
    def mainProjectDir = tempDir.newFolder('test')
    ProtobufPluginTestHelper.copyTestProject(mainProjectDir, 'testProjectCSharpAndJava')

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('build')
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
  }
}