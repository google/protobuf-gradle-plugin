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
  // Current supported version is Android plugin 3.3.0+.
  private static final List<String> GRADLE_VERSION = ["5.0", "5.1.1", "5.4.1"]
  private static final List<String> ANDROID_PLUGIN_VERSION = ["3.3.0", "3.4.0", "3.5.0"]

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
  void "test Utils.isAndroidProject positively detects android project [android #androidPluginVersion, gradle #gradleVersion]"() {
    given: "a project with android plugin"
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder("singleModuleAndroidProject")
       .copyDirs('testProjectAndroid', 'testProjectAndroidBare')
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(mainProjectDir, "build.gradle"), true)

    when: "checkForAndroidPlugin task evaluates Utils.isAndroidProject"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       androidPluginVersion,
       gradleVersion,
       "checkForAndroidPlugin"
    )

    then: "Utils.isAndroidProject evaluation matched assertion in task checkForAndroidPlugin"
    assert result.task(":checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }

  /**
   * Failing test case for https://github.com/google/protobuf-gradle-plugin/issues/236
   */
  @Unroll
  void "test Utils.isAndroidProject returns false on sub project of android project [android #androidPluginVersion, gradle #gradleVersion]"() {
    given: "an android root project and java sub project"
    File subProjectStaging = ProtobufPluginTestHelper.projectBuilder('subModuleTestProjectLite')
       .copyDirs('testProjectLite')
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(subProjectStaging, "build.gradle"), false)
    File mainProjectDir = ProtobufPluginTestHelper.projectBuilder("rootModuleAndroidProject")
       .copyDirs('testProjectAndroid', 'testProjectAndroidBare')
       .copySubProjects(subProjectStaging)
       .build()
    appendUtilIsAndroidProjectCheckTask(new File(mainProjectDir, "build.gradle"), true)

    when: "checkForAndroidPlugin task evaluates Utils.isAndroidProject"
    BuildResult result = buildAndroidProject(
       mainProjectDir,
       androidPluginVersion,
       gradleVersion,
       "checkForAndroidPlugin"
    )

    then: "Utils.isAndroidProject evaluation matched assertion in task checkForAndroidPlugin"
    assert result.task(":checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS
    assert result.task(":subModuleTestProjectLite:checkForAndroidPlugin").outcome == TaskOutcome.SUCCESS

    where:
    androidPluginVersion << ANDROID_PLUGIN_VERSION
    gradleVersion << GRADLE_VERSION
  }
}
