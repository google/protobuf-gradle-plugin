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

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

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
   * For every ExecutableLocator that points to an artifact spec: creates a
   * project configuration dependency for that artifact, registers the
   * configuration dependency as an input dependency with the specified tasks,
   * and adds a doFirst {} block to the specified tasks which resolves the
   * spec, downloads the artifact, and point to the local path.
   */
  void registerTaskDependencies(Collection<GenerateProtoTask> protoTasks) {
    if (protoc.artifact != null) {
      registerDependencyWithTasks(protoc, protoTasks)
    } else if (protoc.path == null) {
      protoc.path = 'protoc'
    }
    for (ExecutableLocator pluginLocator in plugins) {
      if (pluginLocator.artifact != null) {
        registerDependencyWithTasks(pluginLocator, protoTasks)
      } else if (pluginLocator.path == null) {
        pluginLocator.path = "protoc-gen-${pluginLocator.name}"
      }
    }
  }

  void registerDependencyWithTasks(ExecutableLocator locator, Collection<GenerateProtoTask> protoTasks) {
    // create a project configuration dependency for the artifact
    Configuration config = project.configurations.create("protobufToolsLocator_${locator.name}") {
      visible = false
      transitive = false
      extendsFrom = []
    }
    String groupId, artifact, version, classifier, extension
    (groupId, artifact, version, classifier, extension) = artifactParts(locator.artifact)
    Map<String, String> notation = [
            group:groupId,
            name:artifact,
            version:version,
            classifier:classifier ?: project.osdetector.classifier,
            ext:extension ?: 'exe',
    ]
    Dependency dep = project.dependencies.add(config.name, notation)

    for (GenerateProtoTask protoTask in protoTasks) {
      if (protoc.is(locator) || protoTask.hasPlugin(locator.name)) {
        // register the configuration dependency as a task input
        protoTask.inputs.files(config)

        protoTask.doFirst {
          if (locator.path == null) {
            project.logger.info("Resolving artifact: ${notation}")
            File file = config.fileCollection(dep).singleFile
            if (!file.canExecute() && !file.setExecutable(true)) {
              throw new GradleException("Cannot set ${file} as executable")
            }
            project.logger.info("Resolved artifact: ${file}")
            locator.path = file.path
          }
        }
      }
    }
  }

  static List<String> artifactParts(String artifactCoordinate) {
    String artifact
    String extension
    String group
    String name
    String version
    String classifier

    (artifact, extension) = artifactCoordinate.tokenize('@')
    (group, name, version, classifier) = artifact.tokenize(':')

    return [group, name, version, classifier, extension]
  }
}
