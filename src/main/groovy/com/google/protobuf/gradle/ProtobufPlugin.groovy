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

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.GradleException
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.LogLevel
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.CollectionUtils

import javax.inject.Inject

class ProtobufPlugin implements Plugin<Project> {
    private final FileResolver fileResolver

    @Inject
    public ProtobufPlugin(FileResolver fileResolver) {
      this.fileResolver = fileResolver;
    }

    void apply(final Project project) {
        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1).toInteger() < 2 || gv.group(2).toInteger() < 2) {
            //throw new UnsupportedVersionException
            println("You are using Gradle ${project.gradle.gradleVersion}: This version of the protobuf plugin requires minimum Gradle version 2.2")
        }

        if (!project.plugins.hasPlugin('java') && !Utils.isAndroidProject(project)) {
            throw new GradleException('Please apply the Java plugin or the Android plugin first')
        }

        // Provides the osdetector extension
        project.apply plugin: 'osdetector'

        project.convention.plugins.protobuf = new ProtobufConvention(project, fileResolver);

        addSourceSetExtensions(project)
        getSourceSets(project).all { sourceSet ->
          createConfiguration(project, sourceSet.name)
        }
        project.afterEvaluate {
          addProtoTasks(project)
          project.protobuf.tools.resolve()
        }
    }

    /**
     * Creates a configuration if necessary for a source set so that the build
     * author can configure dependencies for it.
     */
    private createConfiguration(Project project, String sourceSetName) {
      String configName = Utils.getConfigName(sourceSetName)
      if (project.configurations.findByName(configName) == null) {
        project.configurations.create(configName) {
          visible = false
          transitive = false
          extendsFrom = []
        }
      }
    }

    /**
     * Adds the proto extension to all SourceSets.
     */
    private addSourceSetExtensions(Project project) {
      getSourceSets(project).all {  sourceSet ->
        sourceSet.extensions.create('proto', ProtobufSourceDirectorySet, project, sourceSet.name, fileResolver)
      }
    }

    private Object getSourceSets(Project project) {
      if (Utils.isAndroidProject(project)) {
        return project.android.sourceSets
      } else {
        return project.sourceSets
      }
    }

    private addProtoTasks(Project project) {
      if (Utils.isAndroidProject(project)) {
        def variants = project.android.hasProperty('libraryVariants') ?
            project.android.libraryVariants : project.android.applicationVariants
        variants.each { variant ->
          addTasksForVariant(project, variant)
        }
      } else {
        getSourceSets(project).each { sourceSet ->
          addTasksForSourceDirSet(project, sourceSet.proto)
        }
      }
    }

    private def addTasksForSourceDirSet(Project project, ProtobufSourceDirectorySet sourceDirSet) {
        def generateJavaTaskName = 'generate' +
            Utils.getSourceSetSubstringForTaskNames(sourceDirSet.name) + 'Proto'
        project.tasks.create(generateJavaTaskName, ProtobufCompile) {
            description = "Compiles Proto source '${sourceDirSet.name}:proto'"
            // Include extracted sources
            ConfigurableFileTree extractedProtoSources =
                project.fileTree("${project.extractedProtosDir}/${sourceDirSet.name}") {
                  include "**/*.proto"
                }
            inputs.source extractedProtoSources
            include extractedProtoSources.dir
            // Include sourceDirSet dirs
            inputs.source sourceDirSet
            sourceDirectorySet = sourceDirSet
            sourceDirSet.srcDirs.each { srcDir ->
              include srcDir
            }

            outputs.dir getGeneratedSourceDir(project, sourceDirSet.name)
            destinationDir = project.file(getGeneratedSourceDir(project, sourceDirSet.name))
        }
        def generateJavaTask = project.tasks.getByName(generateJavaTaskName)

        def extractProtosTaskName = 'extract' +
            Utils.getSourceSetSubstringForTaskNames(sourceDirSet.name) + 'Proto'
        project.tasks.create(extractProtosTaskName, ProtobufExtract) {
            description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
            extractedProtosDir = project.file("${project.extractedProtosDir}/${sourceDirSet.name}")
            configName = Utils.getConfigName(sourceDirSet.name)
        }
        def extractProtosTask = project.tasks.getByName(extractProtosTaskName)
        generateJavaTask.dependsOn(extractProtosTask)

        // Add generated java sources to java source sets that will be compiled.
        SourceSet sourceSet = project.sourceSets.maybeCreate(sourceDirSet.name)
        sourceSet.java.srcDir(getGeneratedSourceDir(project, sourceDirSet.name))
        String compileJavaTaskName = sourceSet.getCompileTaskName("java");
        project.tasks.getByName(compileJavaTaskName).dependsOn(generateJavaTask)
    }

    private def addTasksForVariant(Project project, Object variant) {
      // The android plugin uses its own SourceSetContainer for java source files.
      def sourceSet = project.android.sourceSets.maybeCreate(sourceDirSet.name)
        // Each variant (e.g., release, debug) builds all source sets.
        def variants = project.android.hasProperty('libraryVariants') ?
        project.android.libraryVariants : project.android.applicationVariants
        variants.each { variant ->
          // This automatically adds the output of generateJavaTask to
          // the compile*Java tasks for this variant.
          variant.registerJavaGeneratingTask(generateJavaTask,
              getGeneratedSourceDir(project, sourceDirSet.name) as File)
        }
    }

    private getGeneratedSourceDir(Project project, String sourceSetName) {
        def generatedSourceDir = project.convention.plugins.protobuf.generatedFileDir
        return "${generatedSourceDir}/${sourceSetName}"
    }

}
