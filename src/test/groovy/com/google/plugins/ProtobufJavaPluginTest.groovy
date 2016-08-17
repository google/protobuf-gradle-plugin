package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class ProtobufJavaPluginTest extends Specification {
  private static final def gradleVersions = ["2.12", "3.0"]

  private Project setupBasicProject() {
    Project project = ProjectBuilder.builder().build()
    project.apply plugin: 'java'
    project.apply plugin: 'com.google.protobuf'
    return project
  }

  def "Applying java and com.google.protobuf adds corresponding task to project"() {
    given: "a basic project with java and com.google.protobuf"
    def project = setupBasicProject()

    when: "project evaluated"
    project.evaluate()

    then: "generate tasks added"
    assert project.tasks.generateProto instanceof GenerateProtoTask
    assert project.tasks.generateTestProto instanceof GenerateProtoTask

    assert project.tasks.extractIncludeProto instanceof ProtobufExtract
    assert project.tasks.extractIncludeTestProto instanceof ProtobufExtract
    assert project.tasks.extractProto instanceof ProtobufExtract
    assert project.tasks.extractTestProto instanceof ProtobufExtract
  }

  def "Custom sourceSet should get its own GenerateProtoTask"() {
    given: "a basic project with java and com.google.protobuf"
    def project = setupBasicProject()

    when: "adding custom sourceSet main2"
    project.sourceSets.create('main2')

    and: "project evaluated"
    project.evaluate()

    then: "tasks for main2 added"
    assert project.tasks.generateMain2Proto instanceof GenerateProtoTask

    assert project.tasks.extractIncludeMain2Proto instanceof ProtobufExtract
    assert project.tasks.extractMain2Proto instanceof ProtobufExtract
  }

  def "testProject should be successfully executed"() {
    given: "project from testProject"
    def projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProject')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProject')

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    ['grpc', 'main', 'test'].each {
      def generatedSrcDir = new File(projectDir.path, "build/generated/source/proto/$it")
      def fileList = []
      generatedSrcDir.eachFileRecurse { file ->
        if (file.path.endsWith('.java')) {
          fileList.add (file)
        }
      }
      assert fileList.size > 0
    }

    where:
    gradleVersion << gradleVersions
  }

  def "testProjectLite should be successfully executed"() {
    given: "project from testProjectLite"
    def projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectLite')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProjectLite')

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << gradleVersions
  }

  def "testProjectDependent should be successfully executed"() {
    given: "project from testProject & testProjectDependent"
    def mainProjectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectDependent')
    ProtobufPluginTestHelper.copyTestProjects(mainProjectDir, 'testProject', 'testProjectDependent')

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('testProjectDependent:build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":testProjectDependent:build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << gradleVersions
  }

  def "testProjectCustomProtoDir should be successfully executed"() {
    given: "project from testProjectCustomProtoDir"
    def projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectCustomProtoDir')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProjectCustomProtoDir', )

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << gradleVersions
  }
}
