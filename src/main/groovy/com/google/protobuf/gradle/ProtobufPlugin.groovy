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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.GradleException
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

        if (!project.plugins.hasPlugin('java')
            && !project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException(
                'Please apply the \'java\' plugin or the \'com.android.application\' plugin first')
        }

        // Provides the osdetector extension
        project.apply plugin: 'osdetector'

        project.convention.plugins.protobuf = new ProtobufConvention(project);
        addProtoConfigurations(project)
        addProtoSourceSets(project)
        project.afterEvaluate {
          addProtoTasks(project)
          resolveProtocDep(project)
          resolveNativeCodeGenPlugins(project)
        }
    }

    private addProtoConfigurations(Project project) {
        project.sourceSets.all { SourceSet sourceSet ->
          project.configurations.create(protobufConfigName(sourceSet)) {
            visible = false
            transitive = false
            extendsFrom = []
          }
        }
    }

    private String protobufConfigName(SourceSet sourceSet) {
        return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "protobuf" : sourceSet.getName() + "Protobuf"
    }

    private addProtoSourceSets(Project project) {
      project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
        // The action applies to all source sets, typically 'main' and 'test'
        public void execute(SourceSet sourceSet) {
          final ProtobufSourceSetConvention protobufSourceSetConvention =
              new ProtobufSourceSetConvention(project, ((DefaultSourceSet) sourceSet).getName(),
                  fileResolver)
          // Add all the properties of ProtobufSourceSet to the DefaultSourceSet that you get from
          // sourceSets.main etc. In other words, adds sourceSets.main.proto etc.
          new DslObject(sourceSet).getConvention().getPlugins().put(
              "proto", protobufSourceSetConvention)
        }
      })
    }

    private resolveProtocDep(Project project) {
        String spec = project.convention.plugins.protobuf.protocDep;
        if (spec == null) {
          return;
        }
        Configuration config = project.configurations.create('protoc') {
          visible = false
          transitive = false
          extendsFrom = []
        }
        def groupId, artifact, version
        (groupId, artifact, version) = spec.split(":")
        def notation = [group: groupId,
                        name: artifact,
                        version: version,
                        classifier: project.osdetector.classifier,
                        ext: 'exe']
        project.logger.info('Adding protoc dependency: ' + notation)
        Dependency dep = project.dependencies.add(config.name, notation)
        Set<File> files = config.files(dep)
        File protoc = null
        for (f in files) {
          if (f.getName().endsWith('.exe')) {
            protoc = f
            break
          }
        }
        if (protoc == null) {
          throw new GradleException('Cannot resolve ' + spec)
        }
        if (!protoc.canExecute() && !protoc.setExecutable(true)) {
          throw new GradleException('Cannot make ' + protoc + ' executable')
        }
        project.logger.info('Resolved protoc: ' + protoc)
        project.convention.plugins.protobuf.protocPath = protoc.getPath()
    }

    private resolveNativeCodeGenPlugins(Project project) {
        Configuration config = project.configurations.create('protobufNativeCodeGenPlugins') {
          visible = false
          transitive = false
          extendsFrom = []
        }
        def Map nameToDep = new HashMap()
        for (key in project.convention.plugins.protobuf.protobufNativeCodeGenPluginDeps) {
          def name, groupId, artifact, version
            (name, groupId, artifact, version) = key.split(":")
            def notation = [group: groupId,
                            name: artifact,
                            version: version,
                            classifier: project.osdetector.classifier,
                            ext: 'exe']
            project.logger.info('Adding a native protobuf codegen plugin dependency: ' + notation)
            Dependency dep = project.dependencies.add(config.name, notation)
            nameToDep.put(name, dep);
        }
        for (e in nameToDep) {
            Set<File> files = config.files(e.value);
            File plugin = null
            for (f in files) {
                if (f.getName().endsWith('.exe')) {
                    plugin = f
                    break
                }
            }
            if (plugin == null) {
                throw new GradleException('Cannot resolve ' + e.value)
            }
            if (!plugin.canExecute() && !plugin.setExecutable(true)) {
                throw new GradleException('Cannot make ' + plugin + ' executable')
            }
            project.logger.info('Resolved a native protobuf codegen plugin: ' + plugin)
            project.convention.plugins.protobuf.protobufCodeGenPlugins.add(
                e.key + ':' + plugin.getPath())
        }
    }


    private addProtoTasks(Project project) {
        project.sourceSets.all { SourceSet sourceSet ->
            addTasksToProjectForSourceSet(project, sourceSet)
        }
    }

    private def addTasksToProjectForSourceSet(Project project, SourceSet sourceSet) {
        def generateJavaTaskName = sourceSet.getTaskName('generate', 'proto')
        project.tasks.create(generateJavaTaskName, ProtobufCompile) {
            description = "Compiles Proto source '${sourceSet.name}:proto'"
            // Include extracted sources
            ConfigurableFileTree extractedProtoSources =
                project.fileTree("${project.extractedProtosDir}/${sourceSet.name}") {
                  include "**/*.proto"
                }
            inputs.source extractedProtoSources
            include extractedProtoSources.dir
            // Include sourceSet dirs
            SourceDirectorySet sourceDirSet = project.sourceSets.maybeCreate(sourceSet.name).proto
            inputs.source sourceDirSet
            sourceDirSet.srcDirs.each { srcDir ->
              include srcDir
            }

            outputs.dir getGeneratedSourceDir(project, sourceSet)
            //outputs.upToDateWhen {false}
            sourceDirectorySet = sourceSet.proto
            destinationDir = project.file(getGeneratedSourceDir(project, sourceSet))
        }
        def generateJavaTask = project.tasks.getByName(generateJavaTaskName)

        def extractProtosTaskName = sourceSet.getTaskName('extract', 'proto')
        project.tasks.create(extractProtosTaskName, ProtobufExtract) {
            description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
            //TODO: Figure out why the configuration can't be used as input.  That makes declaring output invalid
            //inputs.files project.configurations[protobufConfigName].files
            //outputs.dir "${project.extractedProtosDir}/${sourceSet.name}"
            extractedProtosDir = project.file("${project.extractedProtosDir}/${sourceSet.name}")
            configName = protobufConfigName(sourceSet)
        }
        def extractProtosTask = project.tasks.getByName(extractProtosTaskName)
        generateJavaTask.dependsOn(extractProtosTask)

        sourceSet.java.srcDir getGeneratedSourceDir(project, sourceSet)
        String compileJavaTaskName = sourceSet.getCompileTaskName("java");
        Task compileJavaTask = project.tasks.getByName(compileJavaTaskName);
        compileJavaTask.dependsOn(generateJavaTask)
    }

    private getGeneratedSourceDir(Project project, SourceSet sourceSet) {
        def generatedSourceDir = project.convention.plugins.protobuf.generatedFileDir
        return "${generatedSourceDir}/${sourceSet.name}"
    }

}
