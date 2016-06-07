package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.TaskGenerator
import com.google.protobuf.gradle.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

class ProtobufJavaPlugin implements Plugin<Project> {

    private Project project

    void apply(final Project project) {
        this.project = project

        project.apply plugin: 'com.google.protobuf.base'

        Utils.setupSourceSets(project, project.sourceSets)
    }

    /**
     * Adds Protobuf-related tasks to the project.
     */
    void addProtoTasks() {
        project.sourceSets.each { sourceSet ->
            addTasksForSourceSet(sourceSet)
        }
    }

    /**
     * Performs after task are added and configured
     */
    void afterTaskAdded() {
        linkGenerateProtoTasksToJavaCompile()
    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private addTasksForSourceSet(final SourceSet sourceSet) {
        def generateProtoTask = TaskGenerator.addGenerateProtoTask(project, sourceSet.name, [sourceSet])
        generateProtoTask.sourceSet = sourceSet
        generateProtoTask.doneInitializing()
        generateProtoTask.builtins {
            java {}
        }

        def extractProtosTask = TaskGenerator.maybeAddExtractProtosTask(project, sourceSet.name)
        generateProtoTask.dependsOn(extractProtosTask)

        // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
        // 'resources' of the output of 'main', in which the source protos are placed.
        // This is nicer than the ad-hoc solution that Android has, because it works for any
        // extended configuration, not just 'testCompile'.
        def extractIncludeProtosTask = TaskGenerator.maybeAddExtractIncludeProtosTask(project, sourceSet.name, sourceSet.compileClasspath)
        generateProtoTask.dependsOn(extractIncludeProtosTask)

        // Include source proto files in the compiled archive, so that proto files from
        // dependent projects can import them.
        def processResourcesTask =
                project.tasks.getByName(sourceSet.getTaskName('process', 'resources'))
        processResourcesTask.from(generateProtoTask.inputs.sourceFiles) {
            include '**/*.proto'
        }
    }

    private linkGenerateProtoTasksToJavaCompile() {
        project.sourceSets.each { sourceSet ->
            def javaCompileTask = project.tasks.getByName(sourceSet.getCompileTaskName("java"))
            project.protobuf.generateProtoTasks.ofSourceSet(sourceSet.name).each { generateProtoTask ->
                javaCompileTask.dependsOn(generateProtoTask)
                generateProtoTask.getAllOutputDirs().each { dir ->
                    javaCompileTask.source project.fileTree(dir: dir)
                }
            }
        }
    }
}