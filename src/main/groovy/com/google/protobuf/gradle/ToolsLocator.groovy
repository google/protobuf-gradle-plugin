/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
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

/**
 * Holds locations of all external executables, i.e., protoc and plugins.
 */
class ToolsLocator {

  private final Project project
  private final ExecutableLocator protoc
  private final NamedDomainObjectContainer<ExecutableLocator> plugins

  ToolsLocator(Project project) {
    this.project = project
    protoc = new ExecutableLocator('protoc')
    plugins = project.container(ExecutableLocator)
  }

  /**
   * For every ExecutableLocator that points to an artifact spec, resolves the
   * spec, downloads the artifact, and point to the local path.
   */
  resolve() {
    resolve(protoc)
    if (protoc.executablePath == null) {
      protoc.executable('protoc')
    }
    plugins.each { plugin ->
      resolve(plugin)
      if (plugin.executablePath == null) {
        plugin.executable("protoc-gen-${plugin.name}")
      }
    }
  }

  static resolve(ExecutableLocator locator) {
    if (locator.artifactSpec != null) {
      Configuration config = project.configurations.create(locator.name) {
        visible = false
        transitive = false
        extendsFrom = []
      }
      def groupId, artifact, version
      (groupId, artifact, version) = locator.artifactSpec.split(":")
      def notation = [group: groupId,
                      name: artifact,
                      version: version,
                      classifier: project.osdetector.classifier,
                      ext: 'exe']
      project.logger.info("Resolving artifact: ${notation}")
      Dependency dep = project.dependencies.add(config.name, notation)
      Set<File> files = config.files(dep)
      File file = null
      for (f in files) {
        if (f.getName().endsWith('.exe')) {
          file = f
          break
        }
      }
      if (file == null) {
        throw new GradleException("Cannot resolve: ${locator.artifactSpec}")
      }
      if (!file.canExecute() && !file.setExecutable(true)) {
        throw new GradleException("Cannot set ${file} as executable")
      }
      project.logger.info("Resolved artifact: ${file}")
      locator.executable(file.path)
    }
  }
}
