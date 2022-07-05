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
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.GradleVersion

import javax.inject.Inject

/**
 * Extracts proto files from a dependency configuration.
 */
@CompileStatic
abstract class ProtobufExtract extends DefaultTask {

  /**
   * The directory for the extracted files.
   */
  private File destDir
  private Boolean isTest = null
  private final ConfigurableFileCollection inputFiles = objectFactory.fileCollection()
  private final CopyActionFacade copyActionFacade = instantiateCopyActionFacade()
  private final ArchiveActionFacade archiveActionFacade = instantiateArchiveActionFacade()
  private final FileCollection filteredProtos = instantiateFilteredProtos()

  public void setIsTest(boolean isTest) {
    this.isTest = isTest
  }

  @Input
  public boolean getIsTest() {
    Preconditions.checkNotNull(isTest)
    return isTest
  }

  @Internal
  public ConfigurableFileCollection getInputFiles() {
    return inputFiles
  }

  /**
   * Inputs for this task containing only proto files, which is enough for up-to-date checks.
   * Add inputs to inputFiles. Uses relative path sensitivity as directory layout changes impact output.
   */
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  FileTree getFilteredProtosFromInputs() {
    return filteredProtos.asFileTree.matching { PatternFilterable pattern -> pattern.include("**/*.proto") }
  }

  @Internal
  CopyActionFacade getCopyActionFacade() {
    return copyActionFacade
  }

  @Internal
  ArchiveActionFacade getArchiveActionFacade() {
    return archiveActionFacade
  }

  @Inject
  abstract ObjectFactory getObjectFactory()

  @Inject
  abstract ProviderFactory getProviderFactory()

  @TaskAction
  void extract() {
    destDir.mkdir()
    FileCollection allProtoFiles = filteredProtos
    copyActionFacade.copy { spec ->
      spec.includeEmptyDirs = false
      spec.from(allProtoFiles)
      spec.include('**/*.proto')
      spec.into(destDir)
      // gradle 7+ requires a duplicate strategy to be explicitly defined
      spec.duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
  }

  protected void setDestDir(File destDir) {
    Preconditions.checkState(this.destDir == null, 'destDir already set')
    this.destDir = destDir
  }

  @OutputDirectory
  protected File getDestDir() {
    return destDir
  }

  private CopyActionFacade instantiateCopyActionFacade() {
    if (GradleVersion.current() >= GradleVersion.version("6.0")) {
      // Use object factory to instantiate as that will inject the necessary service.
      return objectFactory.newInstance(CopyActionFacade.FileSystemOperationsBased)
    }
    return new CopyActionFacade.ProjectBased(project)
  }

  private ArchiveActionFacade instantiateArchiveActionFacade() {
    if (GradleVersion.current() >= GradleVersion.version("6.0")) {
      // Use object factory to instantiate as that will inject the necessary service.
      return objectFactory.newInstance(ArchiveActionFacade.ServiceBased)
    }
    return new ArchiveActionFacade.ProjectBased(project)
  }

  private FileCollection instantiateFilteredProtos() {
    boolean warningLogged = false
    ArchiveActionFacade archiveFacade = this.archiveActionFacade
    Logger logger = this.logger
    // Provider.map seems broken for excluded tasks. Add inputFiles with all contents excluded for
    // the dependency it provides, but then provide the files we actually care about in our own
    // provider. https://github.com/google/protobuf-gradle-plugin/issues/550
    return objectFactory.fileCollection()
        .from(inputFiles.filter { false })
        .from(providerFactory.provider { unused ->
      Set<File> files = inputFiles.files
      PatternSet protoFilter = new PatternSet().include("**/*.proto")
      Set<Object> protoInputs = [] as Set
      for (File file : files) {
        if (file.isDirectory()) {
          protoInputs.add(file)
        } else if (file.path.endsWith('.proto')) {
          if (!warningLogged) {
            warningLogged = true
            logger.warn "proto file '${file.path}' directly specified in configuration. " +
                    "It's likely you specified files('path/to/foo.proto') or " +
                    "fileTree('path/to/directory') in protobuf or compile configuration. " +
                    "This makes you vulnerable to " +
                    "https://github.com/google/protobuf-gradle-plugin/issues/248. " +
                    "Please use files('path/to/directory') instead."
          }
          protoInputs.add(file)
        } else if (file.path.endsWith('.jar') || file.path.endsWith('.zip')) {
          protoInputs.add(archiveFacade.zipTree(file.path).matching(protoFilter))
        } else if (file.path.endsWith('.aar')) {
          FileCollection zipTree = archiveFacade.zipTree(file.path).filter { File it -> it.path.endsWith('.jar') }
          zipTree.each { entry ->
            protoInputs.add(archiveFacade.zipTree(entry).matching(protoFilter))
          }
        } else if (file.path.endsWith('.tar')
                || file.path.endsWith('.tar.gz')
                || file.path.endsWith('.tar.bz2')
                || file.path.endsWith('.tgz')) {
          protoInputs.add(archiveFacade.tarTree(file.path).matching(protoFilter))
        } else {
          logger.debug "Skipping unsupported file type (${file.path}); handles only jar, tar, tar.gz, tar.bz2 & tgz"
        }
      }
      return protoInputs
    })
  }
}
