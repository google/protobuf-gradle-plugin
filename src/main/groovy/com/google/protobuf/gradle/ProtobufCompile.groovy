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
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import org.gradle.util.CollectionUtils

public class ProtobufCompile extends DefaultTask {
    @Input
    def includeDirs = []

    ProtobufSourceDirectorySet sourceDirectorySet

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

    // protoc allows you to prefix comma-delimited options to the path in
    // the --*_out flags, e.g.,
    // - Without options: --java_out=/path/to/output
    // - With options: --java_out=option1,option2:/path/to/output
    // This method generates the prefix out of the given options.
    def String makeOptionsPrefix(List<String> options) {
      StringBuilder prefix = new StringBuilder()
      if (!options.isEmpty()) {
        options.each { option ->
          if (prefix.length() > 0) {
            prefix.append(',')
          }
          prefix.append(option)
        }
        prefix.append(':')
      }
      return prefix.toString()
    }

    @TaskAction
    def compile() {
        HashMap<String, String> pluginPaths = new HashMap<String, String>()
        project.convention.plugins.protobuf.protobufCodeGenPlugins.each { pluginSpec ->
          def splitSpec = splitPluginSpec(pluginSpec)
          def name = splitSpec[0]
          if (splitSpec.length < 2) {
            throw new GradleException("Invalid protoc plugin spec '${pluginSpec}'. Must be 'name:path'")
          }
          def path = splitSpec[1]
          pluginPaths.put(name, path)
        }
        def protoc = project.convention.plugins.protobuf.protocPath
        File destinationDir = project.file(destinationDir)
        Set<File> protoFiles = inputs.sourceFiles.files
        destinationDir.mkdirs()
        def dirs = includeDirs*.path.collect {"-I${it}"}
        logger.debug "ProtobufCompile using directories ${dirs}"
        logger.debug "ProtobufCompile using files ${protoFiles}"
        def cmd = [ protoc ]

        cmd.addAll(dirs)

        // Handle code generation built-ins
        sourceDirectorySet.builtins.each { builtin ->
          String name = builtin.name
          String outPrefix = makeOptionsPrefix(builtin.options)
          cmd += "--${name}_out=${outPrefix}${destinationDir}"
        }
        // Handle code generation plugins
        sourceDirectorySet.plugins.each { plugin ->
          String name = plugin.name
          String path = pluginPaths.get(name)
          if (path == null) {
            // If plugin path is not specified, use the conventional name.
            path = "${project.projectDir}/protoc-gen-${name}"
          }
          String pluginOutPrefix = makeOptionsPrefix(plugin.options)
          cmd += "--${name}_out=${pluginOutPrefix}${destinationDir}"
          cmd += "--plugin=protoc-gen-${name}=${path}"
        }

        cmd.addAll protoFiles
        logger.log(LogLevel.INFO, cmd.toString())
        def stdout = new StringBuffer()
        def stderr = new StringBuffer()
        Process result = cmd.execute()
        result.waitForProcessOutput(stdout, stderr)
        def output = "protoc: stdout: ${stdout}. stderr: ${stderr}"
        if (result.exitValue() == 0) {
            logger.log(LogLevel.INFO, output)
        } else {
            throw new InvalidUserDataException(output)
        }
    }
}
