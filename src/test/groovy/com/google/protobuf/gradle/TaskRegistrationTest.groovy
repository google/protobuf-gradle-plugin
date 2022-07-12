package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import spock.lang.Specification
import spock.lang.Unroll
import org.gradle.testkit.runner.GradleRunner

/**
 * Unit tests for task registration functionality
 */
@CompileDynamic
class TaskRegistrationTest extends Specification {
  // Current supported version is Gradle 5+.
  private static final List<String> GRADLE_VERSIONS = ["5.6", "6.1.1", "6.5", "7.4.2"]
  private static final List<String> AGP_VERSIONS = ["3.5.0", "4.0.0", "4.1.0", "7.2.1"]

  private File testProjectDir = new File(System.getProperty('user.dir'), 'build/tests/taskRegistrationTestProject/')
  private File buildFile = new File(testProjectDir, 'build.gradle')
  private File settingsFile = new File(testProjectDir, 'settings.gradle')
  private File tasksReportFile = new File(testProjectDir, 'tasksReport.txt')

  void setup() {
    testProjectDir.mkdirs()
    testProjectDir.listFiles().each { it.delete() }
  }

  private String generateTaskReportTask = """\
    |tasks.register("generateTaskReport") {
    |  doLast {
    |    File output = project.file(project.generateTaskReportDest)
    |    tasks.all { task ->
    |       // use superclass in reason of gradle runtime decorator
    |       output << "\${task.name} \${task.class.superclass.canonicalName}\\n"
    |    }
    |  }
    |}
  """.stripMargin()

  @Unroll
  void "protobuf plugin tasks should be registered in java project [gradle #gradleVersion]"() {
    given: "project with java protobuf plugins"
    settingsFile << """rootProject.name = 'test-project'"""
    buildFile << """\
      |plugins {
      |  id 'java'
      |  id 'com.google.protobuf'
      |}
      |
      |sourceSets {
      |  internal { }
      |}
      |
      |$generateTaskReportTask
    """.stripMargin()

    when: "tasks task executed"
    def report = runGenerateTaskReport(gradleVersion)

    then: "tasks registered"
    assert report["generateProto"] == GenerateProtoTask
    assert report["extractProto"] == ProtobufExtract
    assert report["extractIncludeProto"] == ProtobufExtract

    assert report["generateTestProto"] == GenerateProtoTask
    assert report["extractTestProto"] == ProtobufExtract
    assert report["extractIncludeTestProto"] == ProtobufExtract

    assert report["generateInternalProto"] == GenerateProtoTask
    assert report["extractInternalProto"] == ProtobufExtract
    assert report["extractIncludeInternalProto"] == ProtobufExtract

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  @Unroll
  void "protobuf plugin tasks should be registered in android project [android #agpVersion, gradle #gradleVersion]"() {
    given: "project with java protobuf plugins"
    settingsFile << """rootProject.name = 'test-project'"""
    buildFile << """\
      |buildscript {
      |  repositories {
      |    google()
      |    jcenter()
      |  }
      |
      |  dependencies {
      |    classpath 'com.android.tools.build:gradle:$agpVersion'
      |  }
      |}
      |
      |plugins {
      |  id 'com.google.protobuf'
      |}
      |apply plugin: 'com.android.application'
      |
      |android {
      |  compileSdkVersion 26
      |  buildToolsVersion "26.0.1"
      |
      |  defaultConfig {
      |    applicationId "io.dont.build.me"
      |    minSdkVersion 7
      |    targetSdkVersion 23
      |    versionCode 1
      |    versionName "1.0"
      |  }
      |
      |  flavorDimensions 'default'
      |  productFlavors {
      |    demo {
      |      dimension 'default'
      |    }
      |    full {
      |      dimension 'default'
      |    }
      |  }
      |
      |  buildTypes {
      |    internal { }
      |  }
      |}
      |
      |$generateTaskReportTask
    """.stripMargin()

    when: "tasks task executed"
    def report = runGenerateTaskReport(gradleVersion)

    then: "tasks registered"
    assert report["extractAndroidTestDebugProto"] == ProtobufExtract
    assert report["extractAndroidTestDemoDebugProto"] == ProtobufExtract
    assert report["extractAndroidTestDemoProto"] == ProtobufExtract
    assert report["extractAndroidTestFullDebugProto"] == ProtobufExtract
    assert report["extractAndroidTestFullProto"] == ProtobufExtract
    assert report["extractAndroidTestProto"] == ProtobufExtract
    assert report["extractDebugProto"] == ProtobufExtract
    assert report["extractDemoDebugProto"] == ProtobufExtract
    assert report["extractDemoInternalProto"] == ProtobufExtract
    assert report["extractDemoProto"] == ProtobufExtract
    assert report["extractDemoReleaseProto"] == ProtobufExtract
    assert report["extractFullDebugProto"] == ProtobufExtract
    assert report["extractFullInternalProto"] == ProtobufExtract
    assert report["extractFullProto"] == ProtobufExtract
    assert report["extractFullReleaseProto"] == ProtobufExtract
    assert report["extractIncludeDemoDebugAndroidTestProto"] == ProtobufExtract
    assert report["extractIncludeDemoDebugProto"] == ProtobufExtract
    assert report["extractIncludeDemoDebugUnitTestProto"] == ProtobufExtract
    assert report["extractIncludeDemoInternalProto"] == ProtobufExtract
    assert report["extractIncludeDemoInternalUnitTestProto"] == ProtobufExtract
    assert report["extractIncludeDemoReleaseProto"] == ProtobufExtract
    assert report["extractIncludeDemoReleaseUnitTestProto"] == ProtobufExtract
    assert report["extractIncludeFullDebugAndroidTestProto"] == ProtobufExtract
    assert report["extractIncludeFullDebugProto"] == ProtobufExtract
    assert report["extractIncludeFullDebugUnitTestProto"] == ProtobufExtract
    assert report["extractIncludeFullInternalProto"] == ProtobufExtract
    assert report["extractIncludeFullInternalUnitTestProto"] == ProtobufExtract
    assert report["extractIncludeFullReleaseProto"] == ProtobufExtract
    assert report["extractIncludeFullReleaseUnitTestProto"] == ProtobufExtract
    assert report["extractInternalProto"] == ProtobufExtract
    assert report["extractProto"] == ProtobufExtract
    assert report["extractReleaseProto"] == ProtobufExtract
    assert report["extractTestDebugProto"] == ProtobufExtract
    assert report["extractTestDemoDebugProto"] == ProtobufExtract
    assert report["extractTestDemoInternalProto"] == ProtobufExtract
    assert report["extractTestDemoProto"] == ProtobufExtract
    assert report["extractTestDemoReleaseProto"] == ProtobufExtract
    assert report["extractTestFullDebugProto"] == ProtobufExtract
    assert report["extractTestFullInternalProto"] == ProtobufExtract
    assert report["extractTestFullProto"] == ProtobufExtract
    assert report["extractTestFullReleaseProto"] == ProtobufExtract
    assert report["extractTestInternalProto"] == ProtobufExtract
    assert report["extractTestProto"] == ProtobufExtract
    assert report["extractTestReleaseProto"] == ProtobufExtract
    assert report["generateDemoDebugAndroidTestProto"] == GenerateProtoTask
    assert report["generateDemoDebugProto"] == GenerateProtoTask
    assert report["generateDemoDebugUnitTestProto"] == GenerateProtoTask
    assert report["generateDemoInternalProto"] == GenerateProtoTask
    assert report["generateDemoInternalUnitTestProto"] == GenerateProtoTask
    assert report["generateDemoReleaseProto"] == GenerateProtoTask
    assert report["generateDemoReleaseUnitTestProto"] == GenerateProtoTask
    assert report["generateFullDebugAndroidTestProto"] == GenerateProtoTask
    assert report["generateFullDebugProto"] == GenerateProtoTask
    assert report["generateFullDebugUnitTestProto"] == GenerateProtoTask
    assert report["generateFullInternalProto"] == GenerateProtoTask
    assert report["generateFullInternalUnitTestProto"] == GenerateProtoTask
    assert report["generateFullReleaseProto"] == GenerateProtoTask
    assert report["generateFullReleaseUnitTestProto"] == GenerateProtoTask

    where:
    gradleVersion << GRADLE_VERSIONS
    agpVersion << AGP_VERSIONS
  }

  private Map<String, Class> runGenerateTaskReport(String gradleVersion) {
    GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments('generateTaskReport', "-PgenerateTaskReportDest=${tasksReportFile.absolutePath}")
        .withPluginClasspath()
        .withGradleVersion(gradleVersion)
        .build()

    Map<String, Class> result = new LinkedHashMap<String, Class>()
    tasksReportFile.readLines().each { line ->
      String[] splitted = line.split(" ")
      if (splitted[1].startsWith("com.google.protobuf.gradle")) {
        result[splitted[0]] = Class.forName(splitted[1])
      }
    }

    return result
  }
}
