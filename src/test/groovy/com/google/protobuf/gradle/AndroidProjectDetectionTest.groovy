package com.google.protobuf.gradle

import static com.google.protobuf.gradle.ProtobufPluginTestHelper.buildAndroidProject

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Verify android projects are identified correctly
 */
class AndroidProjectDetectionTest extends Specification {
  private static final List<String> GRADLE_VERSION = ["5.6"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["3.5.0"]

  static void appendUtilIsAndroidProjectCheckTask(File buildFile, boolean assertResult) {
    buildFile << """
        task checkForAndroidPlugin {
            doLast {
                boolean result = com.google.protobuf.gradle.Utils.isAndroidProject(project)
                println "isAndroidProject -> \$result"
                assert result == ${assertResult}
            }
        }
"""
  }

  @Unroll
  void "test succeeds on android project [android #agpVersion, gradle #gradleVersion]"() {
    given: "a project with android plugin"
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder("singleModuleAndroidProject")
       .copyDirs('testProjectAndroid', 'testProjectAndroidBare')
       .withAndroidPlugin(agpVersion)
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(mainProjectDir, "build.gradle"), true)

    when: "checkForAndroidPlugin task evaluates Utils.isAndroidProject"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       gradleVersion,
       "checkForAndroidPlugin"
    )

    then: "Utils.isAndroidProject evaluation matched assertion in task checkForAndroidPlugin"
    assert result.task(":checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  /**
   * Failing test case for https://github.com/google/protobuf-gradle-plugin/issues/236
   */
  @Unroll
  void "test fails on sub project of android project [android #agpVersion, gradle #gradleVersion]"() {
    given: "an android root project and java sub project"
    File subProjectStaging = ProtobufPluginTestHelper.projectBuilder('subModuleTestProjectLite')
       .copyDirs('testProjectLite')
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(subProjectStaging, "build.gradle"), false)
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder("rootModuleAndroidProject")
       .copyDirs('testProjectAndroid', 'testProjectAndroidBare')
       .copySubProjects(subProjectStaging)
       .withAndroidPlugin(agpVersion)
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(mainProjectDir, "build.gradle"), true)

    when: "checkForAndroidPlugin task evaluates Utils.isAndroidProject"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       gradleVersion,
       "checkForAndroidPlugin"
    )

    then: "Utils.isAndroidProject evaluation matched assertion in task checkForAndroidPlugin"
    assert result.task(":checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS
    assert result.task(":subModuleTestProjectLite:checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS

    where:
    agpVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }
}
