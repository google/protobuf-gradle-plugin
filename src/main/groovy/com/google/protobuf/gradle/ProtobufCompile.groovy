/*
 * Copyright (c) 2015, Alex Antonov. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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

    // '<name>:<path>' -> ['<name>', '<path>']
    // '<name>' -> ['<name>']
    // <path> may be in Windows format, e.g., spec='java:C:\path\to\...'
    def String[] splitPluginSpec(String spec) {
      return spec.split(':', 2)
    }

    @TaskAction
    def compile() {
        def plugins = project.convention.plugins.protobuf.protobufCodeGenPlugins
        def protoc = project.convention.plugins.protobuf.protocPath
        File destinationDir = project.file(destinationDir)
        Set<File> protoFiles = inputs.sourceFiles.files
        destinationDir.mkdirs()
        def dirs = includeDirs*.path.collect {"-I${it}"}
        logger.debug "ProtobufCompile using directories ${dirs}"
        logger.debug "ProtobufCompile using files ${protoFiles}"
        def cmd = [ protoc ]

        cmd.addAll(dirs)
        cmd += "--java_out=${destinationDir}"
        // Handle code generation plugins
        if (plugins) {
            cmd.addAll(plugins.collect {
                def splitSpec = splitPluginSpec(it)
                def name = splitSpec[0]
                "--${name}_out=${destinationDir}"
            })
            cmd.addAll(plugins.collect {
                def splitSpec = splitPluginSpec(it)
                def name = splitSpec[0]
                if (splitSpec.length > 1) {
                    def path = splitSpec[1]
                    "--plugin=protoc-gen-${name}=${path}"
                } else {
                    "--plugin=protoc-gen-${name}=${project.projectDir}/protoc-gen-${name}"
                }
            })
        }

        cmd.addAll protoFiles
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
