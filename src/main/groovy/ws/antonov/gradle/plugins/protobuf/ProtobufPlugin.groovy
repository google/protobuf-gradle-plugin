package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.GradleException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.CollectionUtils

class ProtobufPlugin implements Plugin<Project> {
    void apply(final Project project) {
        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1) < "1" || (gv.group(1) == "1" && gv.group(2) < "12")) {
            //throw new UnsupportedVersionException
            println("You are using Gradle ${project.gradle.gradleVersion}: This version of the protobuf plugin requires minimum Gradle version 1.12")
        }

        project.apply plugin: 'java'

        project.convention.plugins.protobuf = new ProtobufConvention(project);
        project.afterEvaluate {
            addProtoTasks(project)
        }
    }

    private addProtoTasks(Project project) {
        project.sourceSets.all { SourceSet sourceSet ->
            addTasksToProjectForSourceSet(project, sourceSet)
        }
    }

    private def addTasksToProjectForSourceSet(Project project, SourceSet sourceSet) {
        def protobufConfigName = (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "protobuf" : sourceSet.getName() + "Protobuf")

        def generateJavaTaskName = sourceSet.getTaskName('generate', 'proto')
        project.tasks.create(generateJavaTaskName, ProtobufCompile) {
            description = "Compiles Proto source '${sourceSet.name}:proto'"
            inputs.source project.fileTree("src/${sourceSet.name}/proto") {include "**/*.proto"}
            inputs.source project.fileTree("${project.extractedProtosDir}/${sourceSet.name}") {include "**/*.proto"}
            outputs.dir getGeneratedSourceDir(project, sourceSet)
            //outputs.upToDateWhen {false}
            sourceSetName = sourceSet.name
            destinationDir = project.file(getGeneratedSourceDir(project, sourceSet))
        }
        def generateJavaTask = project.tasks.getByName(generateJavaTaskName)

        project.configurations.create(protobufConfigName) {
            visible = false
            transitive = false
            extendsFrom = []
        }
        def extractProtosTaskName = sourceSet.getTaskName('extract', 'proto')
        project.tasks.create(extractProtosTaskName, ProtobufExtract) {
            description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
            //TODO: Figure out why the configuration can't be used as input.  That makes declaring output invalid
            //inputs.files project.configurations[protobufConfigName].files
            //outputs.dir "${project.extractedProtosDir}/${sourceSet.name}"
            extractedProtosDir = project.file("${project.extractedProtosDir}/${sourceSet.name}")
            configName = protobufConfigName
        }
        def extractProtosTask = project.tasks.getByName(extractProtosTaskName)
        generateJavaTask.dependsOn(extractProtosTask)

        sourceSet.java.srcDir getGeneratedSourceDir(project, sourceSet)
        String compileJavaTaskName = sourceSet.getCompileTaskName("java");
        Task compileJavaTask = project.tasks.getByName(compileJavaTaskName);
        compileJavaTask.dependsOn(generateJavaTask)
    }

    private getGeneratedSourceDir(Project project, SourceSet sourceSet) {
        def generatedSourceDir = project.convention.plugins.protobuf.generatedFileDir
				
        return "${generatedSourceDir}/${sourceSet.name}"
    }

}
