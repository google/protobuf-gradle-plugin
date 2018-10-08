/*
 * Original work copyright (c) 2015, Alex Antonov. All rights reserved.
 * Modified work copyright (c) 2015, Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Extracts proto files from a dependency configuration.
 */
class ProtobufExtract extends DefaultTask {

  /**
   * The directory for the extracted files.
   */
  private File destDir
  private Boolean isTest = null

  protected void setDestDir(File destDir) {
    Preconditions.checkState(this.destDir == null, 'destDir already set')
    this.destDir = destDir
    outputs.dir destDir
  }

  public void setIsTest(boolean isTest) {
    this.isTest = isTest
  }

  public boolean getIsTest() {
    Preconditions.checkNotNull(isTest)
    return isTest
  }

  protected File getDestDir() {
    return destDir
  }

  @TaskAction
  void extract() {
    inputs.files.each { file ->
      logger.debug "Extracting protos from ${file} to ${destDir}"
      if (file.isDirectory()) {
        project.copy {
          includeEmptyDirs(false)
          from(file.path) {
            include '**/*.proto'
          }
          into(destDir)
        }
      } else if (file.path.endsWith('.proto')) {
        project.copy {
          includeEmptyDirs(false)
          from(file.path)
          into(destDir)
        }
      } else if (file.path.endsWith('.jar') || file.path.endsWith('.zip')) {
        project.copy {
          includeEmptyDirs(false)
          from(project.zipTree(file.path)) {
            include '**/*.proto'
          }
          into(destDir)
        }
      } else if (file.path.endsWith('.tar')
              || file.path.endsWith('.tar.gz')
              || file.path.endsWith('.tar.bz2')
              || file.path.endsWith('.tgz')) {
        project.copy {
          includeEmptyDirs(false)
          from(project.tarTree(file.path)) {
            include '**/*.proto'
          }
          into(destDir)
        }
      } else {
        logger.debug "Skipping unsupported file type (${file.path}); handles only jar, tar, tar.gz, tar.bz2 & tgz"
      }
    }
  }
}
