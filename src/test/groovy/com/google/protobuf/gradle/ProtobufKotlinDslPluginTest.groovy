package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.testfixtures.ProjectBuilder
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
  private static final List<String> GRADLE_VERSIONS = ["7.6.2", "8.7", "8.9", "8.13"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["7.4.2", "8.5.0", "8.7.0", "8.13.0"]

  @Unroll
  void "test ProtobufDependencyHelper notation [ver=#version, cfg=#configuration, cls=#classifier, ext=#ext]"() {
    given: "a project with java and com.google.protobuf"
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'java'
    project.apply plugin:'com.google.protobuf'
    ProtobufDependencyHelper helper = ProtobufDependencyConfigurationKt.getProtobuf(project.dependencies)

    when: "adding a dependency using named coordinates"
    ExternalModuleDependency dep = helper.invoke('com.example', 'my-protos', version, configuration, classifier, ext)

    then: "dependency coordinates and artifacts are parsed accurately"
    dep.group == 'com.example'
    dep.name == 'my-protos'
    dep.version == version
    dep.targetConfiguration == configuration
    dep.transitive == (ext == null)
    if (classifier != null || ext != null) {
      assert dep.artifacts.size() == 1
      DependencyArtifact artifact = dep.artifacts.first()
      assert artifact.classifier == classifier
      assert artifact.extension == (ext ?: DependencyArtifact.DEFAULT_TYPE)
      assert artifact.type == (ext ?: DependencyArtifact.DEFAULT_TYPE)
    } else {
      assert dep.artifacts.isEmpty()
    }
    project.configurations.getByName('protobuf').dependencies.contains(dep)

    where:
    version | configuration | classifier | ext
    '1.0.0' | null          | null       | null
    '1.0.0' | 'proto'       | null       | null
    '1.0.0' | null          | 'linux'    | null
    '1.0.0' | null          | null       | 'tar.gz'
    '1.0.0' | null          | 'linux'    | 'tar.gz'
    null    | null          | null       | null
    null    | 'custom'      | null       | null
    null    | null          | 'proto'    | null
    null    | null          | 'proto'    | 'tar.gz'
    null    | null          | null       | 'tar.gz'
  }

  @Unroll
  void "testProjectKotlinDsl should be successfully executed (java-only project) [gradle #gradleVersion]"() {
    given: "project from testProjectKotlinDslBase"
    File projectDir = ProtobufPluginTestHelper.projectBuilder(this, 'testProjectKotlinDsl')
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
    File testProjectAndroidKotlinDslStaging = ProtobufPluginTestHelper
            .projectBuilder(this, 'testProjectAndroidKotlinDsl')
            .copyDirs('testProjectAndroidKotlinDsl')
            .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder(this, 'testProjectLite')
            .copyDirs('testProjectLite')
            .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder(this, 'testProjectAndroidDslMain')
            .copySubProjects(testProjectAndroidKotlinDslStaging, testProjectLiteStaging)
            .withAndroidPlugin(agpVersion)
            .build()

    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
        mainProjectDir,
        gradleVersion,
        agpVersion,
        "testProjectAndroidKotlinDsl:build"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroidKotlinDsl:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSIONS
  }
}
