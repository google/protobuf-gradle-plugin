package com.google.protobuf.gradle

import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

/**
 * Unit tests for normal java and kotlin functionality.
 */
class ProtobufJavaPluginTest extends Specification {
  private static final List<String> GRADLE_VERSIONS = ["2.12", "3.0", "4.0", "4.3"]

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

  void "testProject should be successfully executed (java-only project)"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProject')
        .copyDirs('testProjectBase', 'testProject')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build', '--stacktrace')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    verifyProjectDirHelper(projectDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectKotlin should be successfully executed (kotlin-only project)"() {
    given: "project from testProjectKotlin overlaid on testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectKotlin')
        .copyDirs('testProjectBase', 'testProjectKotlin')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    verifyProjectDirHelper(projectDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectJavaAndKotlin should be successfully executed (java+kotlin project)"() {
    given: "project from testProjecJavaAndKotlin overlaid on testProjectKotlin, testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectJavaAndKotlin')
        .copyDirs('testProjectBase', 'testProject', 'testProjectKotlin', 'testProjectJavaAndKotlin')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build')
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS
    verifyProjectDirHelper(projectDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectLite should be successfully executed"() {
    given: "project from testProjectLite"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
        .copyDirs('testProjectBase', 'testProjectLite')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build', '--stacktrace')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectDependent should be successfully executed"() {
    given: "project from testProject & testProjectDependent"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
        .copyDirs('testProjectBase', 'testProject')
        .build()
    File testProjectDependentStaging = ProtobufPluginTestHelper.projectBuilder('testProjectDependent')
        .copyDirs('testProjectDependent')
        .build()

    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectDependentMain')
        .copySubProjects(testProjectStaging, testProjectDependentStaging)
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('testProjectDependent:build', '--stacktrace')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":testProjectDependent:build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProjectCustomProtoDir should be successfully executed"() {
    given: "project from testProjectCustomProtoDir"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProjectCustomProtoDir')
        .copyDirs('testProjectCustomProtoDir')
        .build()

    when: "build is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('build', '--stacktrace')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .withDebug(true)
      .build()

    then: "it succeed"
    result.task(":build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "testProject proto and generated output directories should be added to intellij"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testIdea')
        .copyDirs('testProjectBase', 'testProject')
        .build()

    when: "idea is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('idea')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":idea").outcome == TaskOutcome.SUCCESS
    Node imlRoot = new XmlParser().parse(projectDir.toPath().resolve("testIdea.iml").toFile())
    Collection rootMgr = imlRoot.component.findAll { it.'@name' == 'NewModuleRootManager' }
    assert rootMgr.size() == 1
    assert rootMgr.content.sourceFolder.size() == 1

    Set<String> sourceDir = [] as Set
    Set<String> testSourceDir = [] as Set
    rootMgr.content.sourceFolder[0].each {
      if (Boolean.parseBoolean(it.@isTestSource)) {
        testSourceDir.add(it.@url)
      } else {
        sourceDir.add(it.@url)
      }
    }

    Set<String> expectedSourceDir = ImmutableSet.builder()
        .add('file://$MODULE_DIR$/src/main/java')
        .add('file://$MODULE_DIR$/src/grpc/proto')
        .add('file://$MODULE_DIR$/src/main/proto')
        .add('file://$MODULE_DIR$/build/extracted-include-protos/grpc')
        .add('file://$MODULE_DIR$/build/extracted-protos/main')
        .add('file://$MODULE_DIR$/build/extracted-include-protos/main')
        .add('file://$MODULE_DIR$/build/extracted-protos/grpc')
        .add('file://$MODULE_DIR$/build/generated/source/proto/grpc/java')
        .add('file://$MODULE_DIR$/build/generated/source/proto/grpc/grpc_output')
        .add('file://$MODULE_DIR$/build/generated/source/proto/main/java')
        .build()
    Set<String> expectedTestSourceDir = ImmutableSet.builder()
        .add('file://$MODULE_DIR$/src/test/java')
        .add('file://$MODULE_DIR$/src/test/proto')
        .add('file://$MODULE_DIR$/build/extracted-protos/test')
        .add('file://$MODULE_DIR$/build/extracted-include-protos/test')
        .add('file://$MODULE_DIR$/build/generated/source/proto/test/java')
        .build()
    assert Objects.equals(expectedSourceDir, sourceDir)
    assert Objects.equals(expectedTestSourceDir, testSourceDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  void "test generateCmds should split commands when limit exceeded"() {
    given: "a cmd length limit and two proto files"

    List<String> baseCmd = ["protoc"]
    List<File> protoFiles = [ new File("short.proto"), new File("long_proto_name.proto") ]
    int cmdLengthLimit = 32

    when: "the commands are generated"

    List<List<String>> cmds = GenerateProtoTask.generateCmds(baseCmd, protoFiles, cmdLengthLimit)

    then: "it splits appropriately"
    cmds.size() == 2 && cmds[0] == ["protoc", "short.proto"] && cmds[1] == ["protoc", "long_proto_name.proto"]
  }

  void "test generateCmds should not split commands when under limit"() {
    given: "a cmd length limit and two proto files"

    List<String> baseCmd = ["protoc"]
    List<File> protoFiles = [ new File("short.proto"), new File("long_proto_name.proto") ]
    int cmdLengthLimit = 64

    when: "the commands are generated"

    List<List<String>> cmds = GenerateProtoTask.generateCmds(baseCmd, protoFiles, cmdLengthLimit)

    then: "it splits appropriately"
    cmds.size() == 1 && cmds[0] == ["protoc", "short.proto", "long_proto_name.proto"]
  }

  void "test generateCmds should not return commands when no protos are given"() {
    given: "a cmd length limit and no proto files"

    List<String> baseCmd = ["protoc"]
    List<File> protoFiles = []
    int cmdLengthLimit = 32

    when: "the commands are generated"

    List<List<String>> cmds = GenerateProtoTask.generateCmds(baseCmd, protoFiles, cmdLengthLimit)

    then: "it returns no commands"
    cmds.isEmpty()
  }

  void "test getCmdLengthLimit returns correct limit for Windows"() {
    given: "Windows OS"

    String os = "windows"

    when: "the command length limit is queried"

    int limit = GenerateProtoTask.getCmdLengthLimit(os)

    then: "it returns the XP limit"
    limit == GenerateProtoTask.WINDOWS_CMD_LENGTH_LIMIT
  }

  void "test getCmdLengthLimit returns correct limit for non-Windows OS"() {
    given: "MacOS X at major version 10"

    String os = "Mac OS X"

    when: "the command length limit is queried"

    int limit = GenerateProtoTask.getCmdLengthLimit(os)

    then: "it returns maximum integer value"
    limit == Integer.MAX_VALUE
  }

  private static void verifyProjectDirHelper(File projectDir) {
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
  }
}
