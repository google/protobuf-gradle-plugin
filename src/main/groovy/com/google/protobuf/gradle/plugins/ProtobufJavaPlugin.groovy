package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.TaskGenerator
import com.google.protobuf.gradle.Utils
import javafx.concurrent.Task
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import javax.inject.Inject

class ProtobufJavaPlugin implements Plugin<Project> {

    private Project project
    private final FileResolver fileResolver

    @Inject
    public ProtobufJavaPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(final Project project) {
        this.project = project

        project.apply plugin: 'com.google.protobuf.base'

        Utils.setupSourceSets(project, project.sourceSets, fileResolver)
        project.afterEvaluate {
            addProtoTasks()
            project.protobuf.runTaskConfigClosures()
            // Disallow user configuration outside the config closures, because
            // next in linkGenerateProtoTasksToJavaCompile() we add generated,
            // outputs to the inputs of javaCompile tasks, and any new codegen
            // plugin output added after this point won't be added to javaCompile
            // tasks.
            project.protobuf.generateProtoTasks.all()*.doneConfig()
            linkGenerateProtoTasksToJavaCompile()
        }
    }

    /**
     * Adds Protobuf-related tasks to the project.
     */
    private addProtoTasks(SourceSetContainer sourceSets) {
        sourceSets.each { sourceSet ->
            addTasksForSourceSet(sourceSet)
        }
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