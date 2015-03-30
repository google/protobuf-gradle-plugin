package com.google.protobuf.gradle

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency
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
        // Provides the osdetector extension
        project.apply plugin: 'osdetector'

        project.convention.plugins.protobuf = new ProtobufConvention(project);
        project.afterEvaluate {
          addProtoTasks(project)
          resolveNativeCodeGenPlugins(project)
        }
    }

    private resolveNativeCodeGenPlugins(Project project) {
        Configuration config = project.configurations.create('protobufNativeCodeGenPlugins') {
          visible = false
          transitive = false
          extendsFrom = []
        }
        def Map nameToDep = new HashMap()
        for (key in project.convention.plugins.protobuf.protobufNativeCodeGenPluginDeps) {
          def name, groupId, artifact, version
            (name, groupId, artifact, version) = key.split(":")
            def notation = [group: groupId,
                            name: artifact,
                            version: version,
                            classifier: project.osdetector.classifier,
                            ext: 'exe']
            project.logger.info('Adding a native protobuf codegen plugin dependency: ' + notation)
            Dependency dep = project.dependencies.add(config.name, notation)
            nameToDep.put(name, dep);
        }
        for (e in nameToDep) {
            Set<File> files = config.files(e.value);
            File plugin = null
            for (f in files) {
                if (f.getName().endsWith('.exe')) {
                    plugin = f
                    break
                }
            }
            if (plugin == null) {
                throw new GradleException('Cannot resolve ' + e.value)
            }
            if (!plugin.canExecute() && !plugin.setExecutable(true)) {
                throw new GradleException('Cannot make ' + plugin + ' executable')
            }
            project.logger.info('Resolved a native protobuf codegen plugin: ' + plugin)
            project.convention.plugins.protobuf.protobufCodeGenPlugins.add(
                e.key + ':' + plugin.getPath())
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
