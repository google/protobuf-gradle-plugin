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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Created by aantonov on 5/19/14.
 */
class ProtobufExtract extends DefaultTask {

    File extractedProtosDir

    String configName

    @TaskAction
    def extract() {
        project.configurations[configName].files.each { file ->
            if (file.path.endsWith('.proto')) {
                ant.copy(
                        file: file.path,
                        toDir: extractedProtosDir
                )
                //generateJavaTask.getSource().create(project.files(file))
            } else if (file.path.endsWith('.jar') || file.path.endsWith('.zip')) {
                ant.unzip(src: file.path, dest: extractedProtosDir)
            } else {
                def compression

                if (file.path.endsWith('.tar')) {
                    compression = 'none'
                } else
                if (file.path.endsWith('.tar.gz')) {
                    compression = 'gzip'
                } else if (file.path.endsWith('.tar.bz2')) {
                    compression = 'bzip2'
                } else {
                    throw new GradleException(
                            "Unsupported file type (${file.path}); handles only jar, tar, tar.gz & tar.bz2")
                }

                ant.untar(
                        src: file.path,
                        dest: extractedProtosDir,
                        compression: compression)
            }
        }
    }
}
