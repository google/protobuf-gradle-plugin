package com.google.protobuf.gradle

import com.android.builder.model.Version
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Requires
import spock.lang.Specification

/**
 * Unit tests for utility functions
 */
class UtilsTest extends Specification {

  private Project setupBasicAndroidLibraryProject() {
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'com.android.library'
    return project
  }

  private Project setupThreeZeroZeroUpAndroidProject() {
    Project project = ProjectBuilder.builder().build()
    project.apply plugin:'com.android.base'
    return project
  }

  private Project setupBasicJavaProject(Project parent) {
    Project project = ProjectBuilder.builder().withParent(parent).build()
    project.apply plugin:'java'
    return project
  }

  void "test Utils.isAndroidProject returns false for multi-module java child project"() {
    given: "a multi-module project with a child java project and parent android project"
    Project parent = setupBasicAndroidLibraryProject()
    Project project = setupBasicJavaProject(parent)

    when: "Utils.isAndroidProject examines child java project"
    boolean isAndroidProject = Utils.isAndroidProject(project)

    then: "Utils.isAndroidProject determines the child java project is not an android project"
    assert !isAndroidProject
  }

  void "test Utils.isAndroidProject returns true for basic android library project"() {
    given: "a basic android library project"
    Project project = setupBasicAndroidLibraryProject()

    when: "Utils.isAndroidProject examines project"
    boolean isAndroidProject = Utils.isAndroidProject(project)

    then: "Utils.isAndroidProject determines the basic android project is an android project"
    assert isAndroidProject
  }

  @Requires({ Version.ANDROID_GRADLE_PLUGIN_VERSION.startsWith("3.") })
  void "test Utils.isAndroidProject returns true for marked android project"() {
    given: "a marked android project"
    Project project = setupThreeZeroZeroUpAndroidProject()

    when: "Utils.isAndroidProject examines project"
    boolean isAndroidProject = Utils.isAndroidProject(project)

    then: "Utils.isAndroidProject determines the marked android project is an android project"
    assert isAndroidProject
  }
}
