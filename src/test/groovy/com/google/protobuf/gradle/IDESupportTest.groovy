package com.google.protobuf.gradle

import com.google.common.collect.ImmutableSet
import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests to check ide metadata generation
 */
@CompileDynamic
class IDESupportTest extends Specification {
  // Current supported version is Gradle 5+.
  private static final List<String> GRADLE_VERSIONS = ["5.6", "6.0", "6.7.1", "7.4.2"]

  @Unroll
  void "testProject proto and generated output directories should be added to intellij [gradle #gradleVersion]"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testIdea')
      .copyDirs('testProjectBase', 'testProject')
      .build()

    when: "idea is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('idea')
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":idea").outcome == TaskOutcome.SUCCESS
    Node imlRoot = new XmlParser().parse(projectDir.toPath().resolve("testProject.iml").toFile())
    Collection rootMgr = imlRoot.component.findAll { it.'@name' == 'NewModuleRootManager' }
    assert rootMgr.size() == 1
    assert rootMgr.content.sourceFolder.size() == 1

    Set<String> sourceDir = [] as Set
    Set<String> testSourceDir = [] as Set
    Set<String> generatedDirs = [] as Set
    rootMgr.content.sourceFolder[0].each {
      if (Boolean.parseBoolean(it.@isTestSource)) {
        testSourceDir.add(it.@url)
      } else {
        sourceDir.add(it.@url)
      }
      if (Boolean.parseBoolean(it.@generated)) {
        generatedDirs.add(it.@url)
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
    Set<String> expectedGeneratedDirs = [
      'file://$MODULE_DIR$/build/extracted-include-protos/grpc',
      'file://$MODULE_DIR$/build/extracted-protos/main',
      'file://$MODULE_DIR$/build/extracted-include-protos/main',
      'file://$MODULE_DIR$/build/extracted-protos/grpc',
      'file://$MODULE_DIR$/build/generated/source/proto/grpc/java',
      'file://$MODULE_DIR$/build/generated/source/proto/grpc/grpc_output',
      'file://$MODULE_DIR$/build/generated/source/proto/main/java',
      'file://$MODULE_DIR$/build/extracted-protos/test',
      'file://$MODULE_DIR$/build/extracted-include-protos/test',
      'file://$MODULE_DIR$/build/generated/source/proto/test/java',
    ]
    assert Objects.equals(expectedSourceDir, sourceDir)
    assert Objects.equals(expectedTestSourceDir, testSourceDir)
    Objects.equals(expectedGeneratedDirs, generatedDirs)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  @Unroll
  void "testProject proto and generated output directories should be added to Eclipse [gradle #gradleVersion]"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testEclipse')
      .copyDirs('testProjectBase', 'testProject')
      .build()

    when: "eclipse is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('eclipse')
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":eclipse").outcome == TaskOutcome.SUCCESS
    Node classpathFile = new XmlParser().parse(projectDir.toPath().resolve(".classpath").toFile())
    Collection srcEntries = classpathFile.classpathentry.findAll { it.'@kind' == 'src' }
    assert srcEntries.size() == 6

    Set<String> sourceDir = [] as Set
    srcEntries.each {
      String path = it.@path
      sourceDir.add(path)
      if (path.startsWith("build/generated/source/proto")) {
        if (path.contains("test")) {
          // test source path has one more attribute: ["test"="true"]
          assert it.attributes.attribute.size() == 4
        } else {
          assert it.attributes.attribute.size() == 3
        }
      }
    }

    Set<String> expectedSourceDir = ImmutableSet.builder()
      .add('src/main/java')
      .add('src/test/java')
      .add('build/generated/source/proto/grpc/java')
      .add('build/generated/source/proto/grpc/grpc_output')
      .add('build/generated/source/proto/main/java')
      .add('build/generated/source/proto/test/java')
      .build()
    assert Objects.equals(expectedSourceDir, sourceDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }
}
