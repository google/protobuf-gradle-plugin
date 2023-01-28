package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for android related functionality.
 */
@CompileDynamic
class ProtobufAndroidPluginTest extends Specification {
  private static final List<String> GRADLE_VERSION = ["5.6", "6.5.1", "6.8", "7.4.2"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["3.5.0", "4.1.0", "4.2.0-alpha10", "7.2.1"]

  @Unroll
  void "testProjectAndroid should be successfully executed [android #agpVersion, gradle #gradleVersion]"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
        .copyDirs('testProjectBase', 'testProject')
        .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
        .copyDirs('testProjectAndroidBase', 'testProjectAndroid')
        .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
        .copyDirs('testProjectLite')
        .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidMain')
        .copySubProjects(testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)
        .withAndroidPlugin(agpVersion)
        .build()
    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
        mainProjectDir,
        gradleVersion,
        agpVersion,
        "testProjectAndroid:build"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  @Unroll
  void "testProjectAndroid succeeds with configuration cache [android #agpVersion, gradle #gradleVersion]"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
            .copyDirs('testProjectBase', 'testProject')
            .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
            .copyDirs('testProjectAndroidBase', 'testProjectAndroid')
            .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
            .copyDirs('testProjectLite')
            .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidMain')
            .copySubProjects(testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)
            .withAndroidPlugin(agpVersion)
            .build()
    and:
    GradleRunner runner = ProtobufPluginTestHelper.getAndroidGradleRunner(
            mainProjectDir,
            gradleVersion,
            agpVersion,
            "testProjectAndroid:assembleDebug",
            "-Dorg.gradle.unsafe.configuration-cache=true"
    )
    when: "build is invoked"
    BuildResult result = runner.build()

    then: "it caches the task graph"
    result.output.contains("Calculating task graph")

    and: "it succeed"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.SUCCESS

    when: "build is invoked again"
    result = runner.build()

    then: "it reuses the task graph"
    result.output.contains("Reusing configuration cache")

    and: "it is up to date"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.UP_TO_DATE

    when: "clean is invoked, before a build"
    ProtobufPluginTestHelper.getAndroidGradleRunner(
            mainProjectDir,
            gradleVersion,
            agpVersion,
            "testProjectAndroid:clean",
            "-Dorg.gradle.unsafe.configuration-cache=true"
    ).build()
    result = runner.build()

    then: "it succeed"
    result.task(":testProjectAndroid:assembleDebug").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION.takeRight(1)
    gradleVersion << GRADLE_VERSION.takeRight(1)
  }

  @Unroll
  void "testProjectAndroidDependent [android #agpVersion, gradle #gradleVersion, kotlin #kotlinVersion]"() {
    given: "project from testProjectAndroidLibrary, testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
            .copyDirs('testProjectBase', 'testProject')
            .build()
    File testProjectLibraryStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidLibrary')
            .copyDirs('testProjectAndroidLibrary')
            .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
            .copyDirs('testProjectAndroidDependentBase', 'testProjectAndroid')
            .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidDependentMain')
            .copySubProjects(testProjectStaging, testProjectLibraryStaging, testProjectAndroidStaging)
            .withAndroidPlugin(agpVersion)
            .build()
    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
            mainProjectDir,
            gradleVersion,
            agpVersion,
            "testProjectAndroid:build"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  @Unroll
  void "testProjectAndroid tests build without warnings [android #agpVersion, gradle #gradleVersion]"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    File testProjectStaging = ProtobufPluginTestHelper.projectBuilder('testProject')
            .copyDirs('testProjectBase', 'testProject')
            .build()
    File testProjectAndroidStaging = ProtobufPluginTestHelper.projectBuilder('testProjectAndroid')
            .copyDirs('testProjectAndroidBase', 'testProjectAndroid')
            .build()
    File testProjectLiteStaging = ProtobufPluginTestHelper.projectBuilder('testProjectLite')
            .copyDirs('testProjectLite')
            .build()
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder('testProjectAndroidMain')
            .copySubProjects(testProjectStaging, testProjectLiteStaging, testProjectAndroidStaging)
            .withAndroidPlugin(agpVersion)
            .build()
    when: "build is invoked"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
            mainProjectDir,
            gradleVersion,
            agpVersion,
            "testProjectAndroid:assembleAndroidTest"
    ).build()

    then: "it succeed"
    result.task(":testProjectAndroid:assembleAndroidTest").outcome == TaskOutcome.SUCCESS

    and: "does not contain warnings about proto location"
    !result.output.contains("This makes you vulnerable to https://github.com/google/protobuf-gradle-plugin/issues/248")

    where:
    agpVersion << ANDROID_PLUGIN_VERSION.takeRight(1)
    gradleVersion << GRADLE_VERSION.takeRight(1)
  }

  @Unroll
  void "tests android build should be without eager task creation [android #agpVersion, gradle #gradleVersion]"() {
    given: "a project with android plugin"
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder("singleModuleAndroidProject")
      .copyDirs('testProjectAndroid', 'testProjectAndroidBare')
      .withAndroidPlugin(agpVersion)
      .build()
    new File(mainProjectDir, "build.gradle") << """
    |tasks
    |  // Lifecycle 'clean' task always eager
    |  // https://github.com/gradle/gradle/issues/20864
    |  .matching { it.name != "help" && it.name != "clean" }
    |  .configureEach { throw new IllegalStateException("\$it was eagerly created") }
    """.stripMargin()

    when: "evaluate project by help task"
    BuildResult result = ProtobufPluginTestHelper.getAndroidGradleRunner(
      mainProjectDir,
      gradleVersion,
      agpVersion,
      "help"
    ).build()

    then: "it succeed"
    assert result.task(":help").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION.takeRight(1)
    gradleVersion << GRADLE_VERSION.takeRight(1)
  }
}
