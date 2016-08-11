package com.google.protobuf.gradle.plugins

import com.google.common.collect.ImmutableList
import com.google.protobuf.gradle.TaskGenerator
import com.google.protobuf.gradle.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

class ProtobufAndroidPlugin implements Plugin<Project> {

  private Project project

  void apply(final Project project) {
    this.project = project
    project.apply plugin: 'com.google.protobuf.base'
    Utils.setupSourceSets(project, project.android.sourceSets)
  }

  /**
   * Adds Protobuf-related tasks to the project.
   */
  void addProtoTasks() {
    getNonTestVariants().each { variant ->
      addTasksForVariant(variant, false)
    }
    project.android.testVariants.each { testVariant ->
      addTasksForVariant(testVariant, true)
    }
  }

  /**
   * Performs after task are added and configured
   */
  void afterTaskAdded() {
    linkGenerateProtoTasksToJavaCompile()
  }

  private Object getNonTestVariants() {
    return project.android.hasProperty('libraryVariants') ?
        project.android.libraryVariants : project.android.applicationVariants
  }

  /**
   * Creates Protobuf tasks for a variant in an Android project.
   */
  private addTasksForVariant(final Object variant, final boolean isTestVariant) {
    // The collection of sourceSets that will be compiled for this variant
    def sourceSetNames = new ArrayList()
    def sourceSets = new ArrayList()
    if (isTestVariant) {
      // All test variants will include the androidTest sourceSet
      sourceSetNames.add 'androidTest'
    } else {
      // All non-test variants will include the main sourceSet
        sourceSetNames.add 'main'
    }
    sourceSetNames.add variant.name
    sourceSetNames.add variant.buildType.name
    ImmutableList.Builder<String> flavorListBuilder = ImmutableList.builder()
    if (variant.hasProperty('productFlavors')) {
      variant.productFlavors.each { flavor ->
        sourceSetNames.add flavor.name
        flavorListBuilder.add flavor.name
      }
    }
    sourceSetNames.each { sourceSetName ->
      sourceSets.add project.android.sourceSets.maybeCreate(sourceSetName)
    }

    def generateProtoTask = TaskGenerator.addGenerateProtoTask(project, variant.name, sourceSets)
    generateProtoTask.setVariant(variant, isTestVariant)
    generateProtoTask.flavors = flavorListBuilder.build()
    generateProtoTask.buildType = variant.buildType.name
    generateProtoTask.doneInitializing()
    generateProtoTask.builtins {
      javanano {}
    }

    sourceSetNames.each { sourceSetName ->
      def extractProtosTask = TaskGenerator.maybeAddExtractProtosTask(project, sourceSetName)
      generateProtoTask.dependsOn(extractProtosTask)

      def extractIncludeProtosTask

      // TODO(zhangkun83): Android sourceSet doesn't have compileClasspath. If it did, we
      // haven't figured out a way to put source protos in 'resources'. For now we use an ad-hoc
      // solution that manually includes the source protos of 'main' and its dependencies.
      if (sourceSetName == 'androidTest') {
        extractIncludeProtosTask =
            TaskGenerator.maybeAddExtractIncludeProtosTask (project, sourceSetName,
            project.android.sourceSets['main'].proto, project.configurations['compile'])
      } else {
        extractIncludeProtosTask =
            TaskGenerator.maybeAddExtractIncludeProtosTask(project, sourceSetName)
      }

      generateProtoTask.dependsOn(extractIncludeProtosTask)
    }

    // TODO(zhangkun83): Include source proto files in the compiled archive,
    // so that proto files from dependent projects can import them.
  }

  private void linkGenerateProtoTasksToJavaCompile() {
    (getNonTestVariants() + project.android.testVariants).each { variant ->
      project.protobuf.generateProtoTasks.ofVariant(variant.name).each { generateProtoTask ->
        // This cannot be called once task execution has started
        variant.registerJavaGeneratingTask(generateProtoTask, generateProtoTask.getAllOutputDirs())
      }
    }
  }
}