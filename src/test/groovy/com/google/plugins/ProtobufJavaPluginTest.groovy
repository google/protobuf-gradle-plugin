import com.google.common.collect.ImmutableSet
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
  private static final List<String> GRADLE_VERSIONS = ["2.12", "3.0", "4.0"]

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

  void "testProjectIdea should be successfully executed"() {
    given: "project from testProjectCustomProtoDir"
    File projectDir = ProtobufPluginTestHelper.prepareTestTempDir('testProject')
    ProtobufPluginTestHelper.copyTestProject(projectDir, 'testProject', )

    when: "idea is invoked"
    // The idea plugin does not allow adding dirs that do not exist to sourceDir and testSourceDir.
    // Some of the protobuf directories are only created when 'generateProto' is executed.
    // Failure to run generateProto will result in some directories being silently ignored by
    // the idea plugin.
    GradleRunner.create()
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('generateProto', 'idea')
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":idea").outcome == TaskOutcome.SUCCESS
    def imlRoot = new XmlParser().parse(projectDir.toPath().resolve("testProject.iml").toFile())
    def rootMgr = imlRoot.component.findAll {it.'@name' == 'NewModuleRootManager'}
    assert rootMgr.size() == 1
    assert rootMgr.content.sourceFolder.size() == 1

    Set<String> sourceDir = new HashSet<>()
    Set<String> testSourceDir = new HashSet<>()
    rootMgr.content.sourceFolder[0].each {
      if (Boolean.parseBoolean(it.@isTestSource)) {
        testSourceDir.add(it.@url)
      } else {
        sourceDir.add(it.@url)
      }
    }

    assert Objects.equals(
            sourceDir,
            ImmutableSet.builder()
                    .add('file://$MODULE_DIR$/src/main/java')
                    .add('file://$MODULE_DIR$/src/grpc/proto')
                    .add('file://$MODULE_DIR$/src/main/proto')
                    .add('file://$MODULE_DIR$/build/extracted-include-protos/grpc')
                    .add('file://$MODULE_DIR$/build/extracted-protos/main')
                    .add('file://$MODULE_DIR$/build/extracted-include-protos/main')
                    .add('file://$MODULE_DIR$/build/extracted-protos/grpc')
                    .build())
    assert Objects.equals(
            testSourceDir,
            ImmutableSet.builder()
                    .add('file://$MODULE_DIR$/src/test/java')
                    .add('file://$MODULE_DIR$/src/test/proto')
                    .add('file://$MODULE_DIR$/build/extracted-protos/test')
                    .add('file://$MODULE_DIR$/build/extracted-include-protos/test')
            .build())

    where:
    gradleVersion << GRADLE_VERSIONS
  }
}
