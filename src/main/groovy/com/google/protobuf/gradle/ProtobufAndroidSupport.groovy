/*
 * Copyright (c) 2026, Google Inc. All rights reserved.
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

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.GeneratesTestApk
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant
import com.google.protobuf.gradle.internal.DefaultProtoSourceSet
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

@CompileStatic
class ProtobufAndroidSupport {

  @TypeChecked(TypeCheckingMode.SKIP)
  static void configure(Project project, ProtobufPlugin plugin, Provider<Task> dummyTask) {
    project.android.sourceSets.configureEach { sourceSet ->
      ProtoSourceSet protoSourceSet = plugin.protobufExtension.sourceSets.create(sourceSet.name)
      plugin.addSourceSetExtension(sourceSet, protoSourceSet)
      Configuration protobufConfig = plugin.createProtobufConfiguration(protoSourceSet)
      plugin.setupExtractProtosTask(protoSourceSet, protobufConfig, dummyTask)
    }

    NamedDomainObjectContainer<ProtoSourceSet> variantSourceSets =
        project.objects.domainObjectContainer(ProtoSourceSet) { String name ->
          new DefaultProtoSourceSet(name, project.objects)
        }

    AndroidComponentsExtension androidComponents = project.extensions.getByType(AndroidComponentsExtension)

    androidComponents.onVariants(androidComponents.selector().all()) { Variant variant ->
      List<String> flavors = variant.productFlavors.collect { pair -> pair.second }
      addTasksForVariant(project, plugin, variant, variantSourceSets, dummyTask, flavors, variant.buildType)
      variant.nestedComponents.each { component ->
        addTasksForVariant(project, plugin, component, variantSourceSets, dummyTask, flavors, variant.buildType)
      }
    }
  }

  /**
   * Creates Protobuf tasks for a variant in an Android project.
   */
  @TypeChecked(TypeCheckingMode.SKIP)
  // Don't depend on AGP
  private static void addTasksForVariant(
      Project project,
      ProtobufPlugin plugin,
      Component variant,
      NamedDomainObjectContainer<ProtoSourceSet> variantSourceSets,
      Provider<Task> dummyTask,
      List<String> flavors = [],
      String buildType = null
  ) {
    ProtoSourceSet variantSourceSet = variantSourceSets.create(variant.name)

    FileCollection classpathConfig = variant.compileConfiguration.incoming.artifactView {
      attributes.attribute(Attribute.of("artifactType", String), ArtifactTypeDefinition.JAR_TYPE)
    }.files

    plugin.setupExtractIncludeProtosTask(variantSourceSet, classpathConfig, dummyTask)

    boolean isTest = variant instanceof TestComponent
    boolean isAndroidTest = variant instanceof GeneratesTestApk
    List<String> sourceSetNames = []

    if (isTest) {
      // Allow test variants to access main protos
      if (plugin.protobufExtension.mainSourceSet) {
        variantSourceSet.includesFrom(plugin.protobufExtension.mainSourceSet)
      }
      sourceSetNames.add(isAndroidTest ? "androidTest" : "test")
    } else {
      sourceSetNames.add("main")
      if (buildType) {
        sourceSetNames.add(buildType)
      }
      sourceSetNames.addAll(flavors)
    }
    sourceSetNames.add(variant.name)

    sourceSetNames.each { name ->
      ProtoSourceSet sourceSet = plugin.protobufExtension.sourceSets.findByName(name)
      if (sourceSet) {
        variantSourceSet.extendsFrom(sourceSet)
      }
    }

    Provider<GenerateProtoTask> generateProtoTask =
        plugin.addGenerateProtoTask(variantSourceSet) { GenerateProtoTask task ->
          task.setVariant(variant, isTest)
          task.flavors = flavors
          task.buildType = buildType
          task.doneInitializing()
        }

    variant.sources.java.addGeneratedSourceDirectory(generateProtoTask) { task ->
      task.outputBaseDir
    }

    boolean isLibrary = project.extensions.findByType(LibraryExtension) != null
    if (isLibrary && !isTest) {
      registerProtoSyncTask(project, variant, variantSourceSet)
    }
  }

  // Include source proto files in the compiled archive, so that proto files from dependent projects can import them.
  private static void registerProtoSyncTask(Project project, Component variant, ProtoSourceSet sourceSet) {
    String taskName = "process${variant.name.capitalize()}ProtoResources"

    Provider<ProtobufPlugin.ProtoSyncTask> syncTask = project.tasks
        .register(taskName, ProtobufPlugin.ProtoSyncTask) { task ->
          task.description = "Copies .proto files into the resources for packaging in the AAR."
          task.source.from(sourceSet.proto)
          task.destinationDirectory.set(
              project.layout.buildDirectory.dir("generated/proto-resources/${variant.name}")
          )
        }
    variant.sources.resources.addGeneratedSourceDirectory(syncTask) { task ->
      task.destinationDirectory
    }
  }
}
