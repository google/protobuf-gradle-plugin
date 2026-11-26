package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import kotlin.Unit
import kotlin.jvm.functions.Function1
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
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

  void "test ProtobufDependencyHelper notation with dependencyConfiguration"() {
    given: "a project with java and com.google.protobuf"
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'java'
    project.apply plugin:'com.google.protobuf'
    ProtobufDependencyHelper helper = ProtobufDependencyConfigurationKt.getProtobuf(project.dependencies)
    String observedTargetConfig = null

    when: "adding dependencies using named coordinates and dependencyConfiguration"
    ExternalModuleDependency dep = helper.invoke(
        'com.example', 'my-protos', '1.0.0', 'proto', null, null,
        { ExternalModuleDependency d ->
          observedTargetConfig = d.targetConfiguration
          d.transitive = false
          Unit.INSTANCE
        } as Function1<ExternalModuleDependency, Unit>)
    ExternalModuleDependency overriddenDep = helper.invoke(
        'com.example', 'overridden-protos', null, 'initial', null, null,
        { ExternalModuleDependency d ->
          d.targetConfiguration = 'overridden'
          Unit.INSTANCE
        } as Function1<ExternalModuleDependency, Unit>)
    ExternalModuleDependency artifactDep = helper.invoke(
        'com.example', 'other-protos', null, null, 'proto', 'tar.gz',
        { ExternalModuleDependency d ->
          d.transitive = true
          Unit.INSTANCE
        } as Function1<ExternalModuleDependency, Unit>)

    then: "coordinates, targetConfiguration, and dependencyConfiguration are all applied"
    dep.group == 'com.example'
    dep.name == 'my-protos'
    dep.version == '1.0.0'
    observedTargetConfig == 'proto'
    dep.targetConfiguration == 'proto'
    !dep.transitive
    dep.artifacts.isEmpty()
    project.configurations.getByName('protobuf').dependencies.contains(dep)

    overriddenDep.group == 'com.example'
    overriddenDep.name == 'overridden-protos'
    overriddenDep.version == null
    overriddenDep.targetConfiguration == 'overridden'
    overriddenDep.artifacts.isEmpty()
    project.configurations.getByName('protobuf').dependencies.contains(overriddenDep)

    artifactDep.group == 'com.example'
    artifactDep.name == 'other-protos'
    artifactDep.version == null
    artifactDep.targetConfiguration == null
    artifactDep.transitive
    artifactDep.artifacts.size() == 1
    artifactDep.artifacts.first().classifier == 'proto'
    artifactDep.artifacts.first().extension == 'tar.gz'
    project.configurations.getByName('protobuf').dependencies.contains(artifactDep)
  }

  @Unroll
  void "test ProtobufDependencyHelper multi-string notation throws on Gradle 10+ [#gradleVersion]"() {
    given: "a project with java and com.google.protobuf"
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'java'
    project.apply plugin:'com.google.protobuf'
    ProtobufDependencyHelper helper =
        new ProtobufDependencyHelper('protobuf', project.dependencies, GradleVersion.version(gradleVersion))

    when: "adding a dependency using 6-argument multi-string notation"
    helper.invoke('com.example', 'my-protos', '1.0.0', null, null, null)

    then: "GradleException is thrown"
    GradleException ex = thrown()
    ex.message.contains('Gradle 10+')

    when: "adding a dependency using 7-argument multi-string notation with configuration closure"
    boolean configured = false
    helper.invoke('com.example', 'my-protos', '1.0.0', null, null, null, { ExternalModuleDependency d ->
      configured = true
      Unit.INSTANCE
    } as Function1<ExternalModuleDependency, Unit>)

    then: "GradleException is thrown without executing configuration closure"
    GradleException ex2 = thrown()
    ex2.message.contains('Gradle 10+')
    !configured

    when: "adding a dependency using single-string notation"
    ExternalModuleDependency singleDep = helper.invoke('com.example:my-protos:1.0.0', { ExternalModuleDependency d ->
      d.transitive = false
      Unit.INSTANCE
    } as Function1<ExternalModuleDependency, Unit>)

    then: "single-string notation remains supported"
    singleDep.group == 'com.example'
    singleDep.name == 'my-protos'
    singleDep.version == '1.0.0'
    !singleDep.transitive

    where:
    gradleVersion << ['10.0-milestone-1', '10.0-rc-1', '10.0', '10.1']
  }

  void "test ProtobufDependencyHelper multi-string notation succeeds on Gradle 9.x"() {
    given: "a project with java and com.google.protobuf"
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'java'
    project.apply plugin:'com.google.protobuf'
    ProtobufDependencyHelper helper =
        new ProtobufDependencyHelper('protobuf', project.dependencies, GradleVersion.version('9.9'))

    when: "adding a dependency using multi-string notation"
    ExternalModuleDependency dep = helper.invoke('com.example', 'my-protos', '1.0.0', null, null, null)

    then: "dependency is added"
    dep.group == 'com.example'
    dep.name == 'my-protos'
    dep.version == '1.0.0'
    project.configurations.getByName('protobuf').dependencies.contains(dep)
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
