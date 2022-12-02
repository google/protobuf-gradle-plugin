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

import com.google.protobuf.gradle.api.ProtoVariantSelector
import com.google.protobuf.gradle.internal.ProtoSourceSetObjectFactory
import com.google.protobuf.gradle.internal.ProtoVariantObjectFactory
import com.google.protobuf.gradle.api.GenerateProtoTaskSpec
import com.google.protobuf.gradle.internal.DefaultProtoVariantSelector
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty

import javax.inject.Inject

/**
 * Adds the protobuf {} block as a property of the project.
 */
@CompileStatic // gradle require abstract modificator on extensions
@SuppressWarnings(["AbstractClassWithoutAbstractMethod", "AbstractClassWithPublicConstructor"])
abstract class ProtobufExtension {
  private final Project project
  private final ToolsLocator tools
  private final NamedDomainObjectContainer<ProtoSourceSet> sourceSets
  private final NamedDomainObjectContainer<ProtoVariant> variants

  /**
   * The base directory of generated files.
   * The default is: "${project.buildDir}/generated/source/proto".
   */
  @Inject
  abstract DirectoryProperty getOutputBaseDir()

  public ProtobufExtension(final Project project) {
    this.project = project
    this.tools = new ToolsLocator(project)
    this.outputBaseDir.convention(project.layout.buildDirectory.dir("generated/source/proto"))
    this.sourceSets = project.objects
      .domainObjectContainer(ProtoSourceSet, new ProtoSourceSetObjectFactory(project.objects))
    this.variants = project.objects
      .domainObjectContainer(ProtoVariant, new ProtoVariantObjectFactory(project.objects))
  }

  @PackageScope
  NamedDomainObjectContainer<ProtoSourceSet> getSourceSets() {
    return this.sourceSets
  }

  @PackageScope
  NamedDomainObjectContainer<ProtoVariant> getVariants() {
    return this.variants
  }

  @PackageScope
  ToolsLocator getTools() {
    return tools
  }

  //===========================================================================
  //         Configuration methods
  //===========================================================================

  /**
   * Locates the protoc executable. The closure will be manipulating an
   * ExecutableLocator.
   */
  public void protoc(Action<ExecutableLocator> configureAction) {
    configureAction.execute(tools.protoc)
  }

  /**
   * Locate the codegen plugin executables. The closure will be manipulating a
   * NamedDomainObjectContainer<ExecutableLocator>.
   */
  public void plugins(Action<NamedDomainObjectContainer<ExecutableLocator>> configureAction) {
    configureAction.execute(tools.plugins)
  }

  ProtoVariantSelector variantSelector() {
    return new DefaultProtoVariantSelector()
  }

  void variants(
    ProtoVariantSelector selector = variantSelector(),
    Action<GenerateProtoTaskSpec> configureAction
  ) {
    this.variants.all { ProtoVariant variant ->
      if ((selector as DefaultProtoVariantSelector).select(variant)) {
        configureAction.execute(variant.generateProtoTaskSpec)
      }
    }
  }
}
