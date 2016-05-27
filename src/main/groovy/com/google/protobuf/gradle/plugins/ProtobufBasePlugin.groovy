package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.ProtobufConvention
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver

import javax.inject.Inject

class ProtobufBasePlugin implements Plugin<Project> {

    final FileResolver fileResolver

    @Inject
    public ProtobufBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(final Project project) {
        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1).toInteger() != 2 || gv.group(2).toInteger() < 12) {
            project.logger.error("You are using Gradle ${project.gradle.gradleVersion}: "
                    + " This version of the protobuf plugin works with Gradle version 2.12+")
        }

        // Provides the osdetector extension
        project.apply plugin: 'osdetector'

        project.convention.plugins.protobuf = new ProtobufConvention(project);

        project.afterEvaluate {
          // protoc and codegen plugin configuration may change through the protobuf{}
          // block. Only at this point the configuration has been finalized.
          project.protobuf.tools.registerTaskDependencies(project.protobuf.generateProtoTasks.all())
        }
    }
}