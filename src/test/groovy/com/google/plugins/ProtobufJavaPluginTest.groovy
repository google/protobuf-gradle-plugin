package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

/**
 * Unit tests for normal java functionality.
 */
class ProtobufJavaPluginTest extends Specification {
  private static final List<String> GRADLE_VERSIONS = ["2.12", "3.0", "4.0", "4.3-rc-2"]

  private Project setupBasicProject() {
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'java'
    project.apply plugin:'com.google.protobuf'
    return project
  }

  void "testApplying java and com.google.protobuf adds corresponding task to project"() {
    given: "a basic project with java and com.google.protobuf"
    Project project = setupBasicProject()

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

  void "testCustom sourceSet should get its own GenerateProtoTask"() {
    given: "a basic project with java and com.google.protobuf"
    Project project = setupBasicProject()

    when: "adding custom sourceSet main2"
    project.sourceSets.create('main2')

    and: "project evaluated"
    project.evaluate()

    then: "tasks for main2 added"
    assert project.tasks.generateMain2Proto instanceof GenerateProtoTask

    assert project.tasks.extractIncludeMain2Proto instanceof ProtobufExtract
    assert project.tasks.extractMain2Proto instanceof ProtobufExtract
  }

  void "testProject should be successfully executed"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProject')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProject')

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    ['grpc', 'main', 'test'].each {
      File generatedSrcDir = new File(projectDir.path, "build/generated/source/proto/$it")
      List<File> fileList = []
      generatedSrcDir.eachFileRecurse { file ->
        if (file.path.endsWith('.java')) {
          fileList.add (file)
        }
      }
      assert fileList.size > 0
    }

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectLite should be successfully executed"() {
    given: "project from testProjectLite"
    File projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectLite')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProjectLite')

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectDependent should be successfully executed"() {
    given: "project from testProject & testProjectDependent"
    File mainProjectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectDependent')
    ProtobufPluginTestHelper.copyTestProjects(mainProjectDir, 'testProject', 'testProjectDependent')

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('testProjectDependent:build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":testProjectDependent:build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectCustomProtoDir should be successfully executed"() {
    given: "project from testProjectCustomProtoDir"
    File projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProjectCustomProtoDir')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProjectCustomProtoDir', )

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }
}
