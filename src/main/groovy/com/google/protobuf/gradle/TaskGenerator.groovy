package com.google.protobuf.gradle

import com.google.common.collect.ImmutableList

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.SourceSet

class TaskGenerator {

    /**
     * Adds a task to run protoc and compile all proto source files for a sourceSet or variant.
     *
     * @param sourceSetOrVariantName the name of the sourceSet (Java) or
     * variant (Android) that this task will run for.
     *
     * @param sourceSets the sourceSets that contains the proto files to be
     * compiled. For Java it's the sourceSet that sourceSetOrVariantName stands
     * for; for Android it's the collection of sourceSets that the variant includes.
     */
    static Task addGenerateProtoTask(Project project, String sourceSetOrVariantName, Collection<Object> sourceSets) {
        def generateProtoTaskName = 'generate' +
                Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Proto'
        return project.tasks.create(generateProtoTaskName, GenerateProtoTask) {
            description = "Compiles Proto source for '${sourceSetOrVariantName}'"
            outputBaseDir = "${project.protobuf.generatedFilesBaseDir}/${sourceSetOrVariantName}"
            sourceSets.each { sourceSet ->
                // Include sources
                inputs.source sourceSet.proto
                ProtobufSourceDirectorySet protoSrcDirSet = sourceSet.proto
                protoSrcDirSet.srcDirs.each { srcDir ->
                    include srcDir
                }

                // Include extracted sources
                ConfigurableFileTree extractedProtoSources =
                        project.fileTree(getExtractedProtosDir(sourceSet.name)) {
                            include "**/*.proto"
                        }
                inputs.source extractedProtoSources
                include extractedProtoSources.dir

                // Register extracted include protos
                ConfigurableFileTree extractedIncludeProtoSources =
                        project.fileTree(getExtractedIncludeProtosDir(sourceSet.name)) {
                            include "**/*.proto"
                        }
                // Register them as input, but not as "source".
                // Inputs are checked in incremental builds, but only "source" files are compiled.
                inputs.dir extractedIncludeProtoSources
                // Add the extracted include dir to the --proto_path include paths.
                include extractedIncludeProtoSources.dir
            }
        }
    }

    /**
     * Adds a task to extract protos from protobuf dependencies. They are
     * treated as sources and will be compiled.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    static Task maybeAddExtractProtosTask(Project project, String sourceSetName) {
        def extractProtosTaskName = 'extract' +
                Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Proto'
        Task existingTask = project.tasks.findByName(extractProtosTaskName)
        if (existingTask != null) {
            return existingTask
        }
        return project.tasks.create(extractProtosTaskName, ProtobufExtract) {
            description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
            destDir = getExtractedProtosDir(sourceSetName) as File
            inputs.files project.configurations[Utils.getConfigName(sourceSetName, 'protobuf')]
        }
    }

    /**
     * Adds a task to extract protos from compile dependencies of a sourceSet,
     * if there isn't one. Those are needed for imports in proto files, but
     * they won't be compiled since they have already been compiled in their
     * own projects or artifacts.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    static Task maybeAddExtractIncludeProtosTask(Project project, String sourceSetName, def... inputFilesList) {
        def extractIncludeProtosTaskName = 'extractInclude' +
                Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Proto'
        Task existingTask = project.tasks.findByName(extractIncludeProtosTaskName)
        if (existingTask != null) {
            return existingTask
        }
        return project.tasks.create(extractIncludeProtosTaskName, ProtobufExtract) {
            description = "Extracts proto files from compile dependencies for includes"
            destDir = getExtractedIncludeProtosDir(sourceSetName) as File
            inputs.files project.configurations[Utils.getConfigName(sourceSetName, 'compile')]

            // TL; DR: Make protos in 'test' sourceSet able to import protos from the 'main' sourceSet.
            // Sub-configurations, e.g., 'testCompile' that extends 'compile', don't depend on the
            // their super configurations. As a result, 'testCompile' doesn't depend on 'compile' and
            // it cannot get the proto files from 'main' sourceSet through the configuration. However,
            inputFilesList.each({ inputFiles -> inputs.files inputFiles })
        }
    }

    private String getExtractedIncludeProtosDir(Project project, String sourceSetName) {
        return "${project.buildDir}/extracted-include-protos/${sourceSetName}"
    }

    private String getExtractedProtosDir(Project project, String sourceSetName) {
        return "${project.buildDir}/extracted-protos/${sourceSetName}"
    }
}