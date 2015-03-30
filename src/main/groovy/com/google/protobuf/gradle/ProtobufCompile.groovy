package com.google.protobuf.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import org.gradle.util.CollectionUtils

public class ProtobufCompile extends DefaultTask {
    @Input
    def includeDirs = []

    String sourceSetName

    String destinationDir

    /**
     * Add a directory to protoc's include path.
     */
    public void include(Object dir) {
        if (dir instanceof File) {
            includeDirs += dir
        } else {
            includeDirs += project.file(dir)
        }
    }

    @TaskAction
    def compile() {
        def plugins = project.convention.plugins.protobuf.protobufCodeGenPlugins
        def protoc = project.convention.plugins.protobuf.protocPath
        File destinationDir = project.file(destinationDir)
        def srcDirs = [project.file("src/${sourceSetName}/proto"), "${project.extractedProtosDir}/${sourceSetName}"]

        destinationDir.mkdirs()
        def dirs = CollectionUtils.join(" -I", srcDirs)
        logger.debug "ProtobufCompile using directories ${dirs}"
        logger.debug "ProtobufCompile using files ${inputs.sourceFiles.files}"
        def cmd = [ protoc ]

        cmd.addAll(srcDirs.collect {"-I${it}"})
        //TODO: Figure out how to add variable to a task
        cmd.addAll(includeDirs*.path.collect {"-I${it}"})
        cmd += "--java_out=${destinationDir}"
        // Handle code generation plugins
        if (plugins) {
            cmd.addAll(plugins.collect {
                def name = it
                if (it.indexOf(":") > 0) {
                    name = it.split(":")[0]
                }
                "--${name}_out=${destinationDir}"
            })
            cmd.addAll(plugins.collect {
                if (it.indexOf(":") > 0) {
                    def values = it.split(":")
                    "--plugin=protoc-gen-${values[0]}=${values[1]}"
                } else {
                    "--plugin=protoc-gen-${it}=${project.projectDir}/protoc-gen-${it}"
                }
            })
        }

        cmd.addAll inputs.sourceFiles.files
        logger.log(LogLevel.INFO, cmd.toString())
        def output = new StringBuffer()
        Process result = cmd.execute()
        result.consumeProcessOutput(output, output)
        result.waitFor()
        if (result.exitValue() == 0) {
            logger.log(LogLevel.INFO, output.toString())
        } else {
            throw new InvalidUserDataException(output.toString())
        }
    }
}
