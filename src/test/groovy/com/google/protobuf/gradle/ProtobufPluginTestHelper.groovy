package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

/**
 * Utility class.
 */
@CompileDynamic
final class ProtobufPluginTestHelper {
  private ProtobufPluginTestHelper() {
    // do not instantiate
  }

  static void verifyProjectDir(File projectDir) {
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

  static BuildResult buildAndroidProject(
      File mainProjectDir,
      String gradleVersion,
      String agpVersion,
      String fullPathTask,
      String... arguments
  ) {
    return getAndroidGradleRunner(mainProjectDir, gradleVersion, agpVersion, fullPathTask, arguments).build()
  }

  static GradleRunner getAndroidGradleRunner(
      File mainProjectDir,
      String gradleVersion,
      String agpVersion,
      String fullPathTask,
      String... arguments
  ) {
    File localBuildCache = new File(mainProjectDir, ".buildCache")
    if (localBuildCache.exists()) {
      localBuildCache.deleteDir()
    }
    List<String> args = arguments.toList()
    // set android build cache to avoid using home directory on CI.
    // More details about that if can be found here:
    // https://developer.android.com/studio/releases/gradle-plugin.html#build-cache-removed
    args.add("-Dorg.gradle.caching=false")
    if (agpVersion.take(1).toInteger() <= 4) { // TODO: improve version comparison
      args.add("-Pandroid.buildCacheDir=$localBuildCache".toString())
    }

    args.add(fullPathTask)
    args.add("--stacktrace")
    return GradleRunner.create()
       .withProjectDir(mainProjectDir)
       .withArguments(args)
       .withPluginClasspath()
       .withGradleVersion(gradleVersion)
       .forwardStdOutput(new OutputStreamWriter(System.out))
       .forwardStdError(new OutputStreamWriter(System.err))
    // Enabling debug causes the test to fail with Android plugin version 3.3.0+.
    // See https://docs.gradle.org/current/javadoc/org/gradle/testkit/runner/GradleRunner.html#isDebug--
    // .withDebug(true)
  }

  /**
   * Creates a temp test dir with name {@code testProjectName}, which is generated by
   * copying a list of overlay dirs on top of it.
   */
  static TestProjectBuilder projectBuilder(String projectName) {
    return new TestProjectBuilder(projectName)
  }

  static final class TestProjectBuilder {
    String testProjectName
    List<String> sourceDirs = []
    List<File> subProjects = []
    String androidPluginVersion
    String kotlinVersion

    private TestProjectBuilder(String projectName) {
      this.testProjectName = projectName
    }

    TestProjectBuilder copyDirs(String... dirs) {
      sourceDirs.addAll(dirs)
      return this
    }

    TestProjectBuilder copySubProjects(File... subProjects) {
      this.subProjects.addAll(subProjects)
      return this
    }

    TestProjectBuilder withAndroidPlugin(String androidPluginVersion) {
      this.androidPluginVersion = androidPluginVersion
      return this
    }

    TestProjectBuilder withKotlin(String kotlinVersion) {
      this.kotlinVersion = kotlinVersion
      return this
    }

    File build() {
      File projectDir = new File(System.getProperty('user.dir'), 'build/tests/' + testProjectName)
      FileUtils.deleteDirectory(projectDir)
      FileUtils.forceMkdir(projectDir)
      sourceDirs.each {
        FileUtils.copyDirectory(new File(System.getProperty("user.dir"), it), projectDir)
      }

      if (subProjects) {
        File settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.createNewFile()

        subProjects.each {
          File subProjectDir = new File(projectDir.path, it.name)
          FileUtils.copyDirectory(it, subProjectDir)

          settingsFile << """
          include ':$it.name'
          project(':$it.name').projectDir = "\$rootDir/$it.name" as File
          """
        }
      }

      // Do not need to create a buildscript, the test project either has one from the template
      // or it is a composite build that does not require a top-level buildscript.

      if (androidPluginVersion != null || kotlinVersion != null) {
        File buildFile = new File(projectDir.path, "build.gradle")
        buildFile.createNewFile()
        List<String> previousFileContents = buildFile.readLines()
        buildFile.delete()
        buildFile.createNewFile()

        buildFile << """
buildscript {
    ${androidPluginVersion ? "ext.androidPluginVersion = \"${androidPluginVersion}\"" : ""}
    ${kotlinVersion ? "ext.kotlinVersion = \"${kotlinVersion}\"" : ""}
    repositories {
        // JCenter has a broken upload of lint-gradle-api-26.1.2, as it is missing the JAR. So we
        // put thet Google repo first
        maven { url "https://dl.google.com/dl/android/maven2/" }
        jcenter()
        maven { url "https://plugins.gradle.org/m2/" }
    }
    dependencies {
        ${androidPluginVersion ? "classpath \"com.android.tools.build:gradle:\$androidPluginVersion\"" : ""}
        ${kotlinVersion ? "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:\$kotlinVersion\"" : ""}
    }
}
"""
        previousFileContents.each { line ->
          buildFile << line + '\n'
        }
      }

      return projectDir
    }
  }
}
