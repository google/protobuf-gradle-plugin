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

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.GradleException
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.LogLevel
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
          // The Android variants are only available at this point.
          addProtoTasks(project)
          project.protobuf.runTaskConfigClosures()
          // protoc and codegen plugin configuration may change through the protobuf{}
          // block. Only at this point the configuration has been finalized.
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
     * Adds the proto extension to all SourceSets, e.g., it creates
     * sourceSets.main.proto and sourceSets.test.proto.
     */
    private addSourceSetExtensions(Project project) {
      getSourceSets(project).all {  sourceSet ->
        sourceSet.extensions.create('proto', ProtobufSourceDirectorySet, project, sourceSet.name, fileResolver)
      }
    }

    /**
     * Returns the sourceSets container of a Java or an Android project.
     */
    private Object getSourceSets(Project project) {
      if (Utils.isAndroidProject(project)) {
        return project.android.sourceSets
      } else {
        return project.sourceSets
      }
    }

    /**
     * Adds Protobuf-related tasks to the project.
     */
    private addProtoTasks(Project project) {
      if (Utils.isAndroidProject(project)) {
        def variants = project.android.hasProperty('libraryVariants') ?
            project.android.libraryVariants : project.android.applicationVariants
        variants.each { variant ->
          addTasksForVariant(project, variant)
        }
      } else {
        getSourceSets(project).each { sourceSet ->
          addTasksForSourceSet(project, sourceSet)
        }
      }
    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private def addTasksForSourceSet(final Project project, final SourceSet sourceSet) {
      def generateProtoTaskName = 'generate' +
          Utils.getSourceSetSubstringForTaskNames(sourceSet.name) + 'Proto'
      final String extractedProtosDir = "${project.buildDir}/extracted-protos/${sourceSet.name}"
      def generateProtoTask = project.tasks.create(generateProtoTaskName, GenerateProtoTask) {
        description = "Compiles Proto source for sourceSet '${sourceSet.name}'"
        outputBaseDir = "${project.protobuf.generatedFilesBaseDir}/${sourceSet.name}"
        // Include extracted sources
        ConfigurableFileTree extractedProtoSources =
            project.fileTree(extractedProtosDir) {
              include "**/*.proto"
            }
        inputs.source extractedProtoSources
        include extractedProtoSources.dir
        inputs.source sourceSet.proto
        ProtobufSourceDirectorySet protoSrcDirSet = sourceSet.proto
        protoSrcDirSet.srcDirs.each { srcDir ->
          include srcDir
        }
      }

      generateProtoTask.sourceSet = sourceSet
      generateProtoTask.doneInitializing()
      generateProtoTask.builtins {
        java {}
      }

      def extractProtosTaskName = 'extract' +
          Utils.getSourceSetSubstringForTaskNames(sourceSet.name) + 'Proto'
      project.tasks.create(extractProtosTaskName, ProtobufExtract) {
          description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
          destDir = extractedProtosDir as File
          configName = Utils.getConfigName(sourceSet.name)
      }
      def extractProtosTask = project.tasks.getByName(extractProtosTaskName)
      generateProtoTask.dependsOn(extractProtosTask)

      String compileJavaTaskName = sourceSet.getCompileTaskName("java");
      project.tasks.getByName(compileJavaTaskName).dependsOn(generateProtoTask)
    }

    /**
     * Creates Protobuf tasks for a variant in an Android project.
     */
    private def addTasksForVariant(final Project project, final Object variant) {
      // The collection of sourceSets that will be compiled for this variant
      def sourceSetNames = new ArrayList()
      def sourceSets = new ArrayList()
      // TODO(zhangkun83): it seems all variants will include the sources under
      // main. Is it the case?
      sourceSetNames.add 'main'
      sourceSetNames.add variant.name
      sourceSetNames.add variant.buildType.name
      ImmutableList.Builder<String> flavorListBuilder = ImmutableList.builder()
      if (variant.hasProperty('productFlavors')) {
        variant.productFlavors.each { flavor ->
          sourceSetNames.add flavor.name
          flavorListBuilder.add flavor.name
        }
      }
      sourceSetNames.each { sourceSetName ->
        def sourceSet = project.android.sourceSets.findByName(sourceSetName)
        if (sourceSet != null) {
          sourceSets.add sourceSet
        }
      }

      def generateProtoTaskName = "generate${variant.name}Proto"
      def generateProtoTask = project.tasks.create(generateProtoTaskName, GenerateProtoTask) {
        description = "Compiles Proto source for variant '${variant.name}'"
        outputBaseDir = "${project.protobuf.generatedFilesBaseDir}/${variant.name}"
        // TODO(zhangkun83): include extracted protos?
        sourceSets.each { sourceSet ->
          ProtobufSourceDirectorySet protoSrcDirSet = sourceSet.proto
          inputs.source protoSrcDirSet
          protoSrcDirSet.srcDirs.each { srcDir ->
            include srcDir
          }
        }
      }

      generateProtoTask.variant = variant
      generateProtoTask.flavors = flavorListBuilder.build()
      generateProtoTask.buildType = variant.buildType
      generateProtoTask.doneInitializing()
      generateProtoTask.builtins {
        javanano {}
      }

      // TODO(zhangkun83): create extraction tasks?

      // At this point we have not decided all the protoc outputs, but we need
      // to register the task to the variant so that the variant can make Java
      // compilation tasks depend on the generateProto task. We will call this
      // again in GenerateProtoTask.compile() with all output dirs when they
      // are finalized.
      variant.registerJavaGeneratingTask(generateProtoTask)
    }
}
