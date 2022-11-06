/*
 * Copyright (c) 2022, Google Inc. All rights reserved.
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
package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.Nullable

@CompileStatic
class DefaultProtoSourceSet implements ProtoSourceSet {
  private final String name
  private final SourceDirectorySet proto
  private final SourceDirectorySet includeProtoDirs
  private final SourceDirectorySet output

  DefaultProtoSourceSet(String name, ObjectFactory objects) {
    this.name = name
    this.proto = objects.sourceDirectorySet("proto", "${name.capitalize()} Proto Source")
    this.includeProtoDirs = objects.sourceDirectorySet("proto", "${name.capitalize()} Proto Include Dirs")
    this.output = objects.sourceDirectorySet("compiled", "${name.capitalize()} Compiled Proto")
  }

  @Override
  void includesFrom(ProtoSourceSet protoSourceSet) {
    this.includeProtoDirs.source(protoSourceSet.proto)
    this.includeProtoDirs.source(protoSourceSet.includeProtoDirs)
  }

  @Override
  void extendsFrom(ProtoSourceSet protoSourceSet) {
    this.proto.source(protoSourceSet.proto)
    this.includeProtoDirs.source(protoSourceSet.includeProtoDirs)
  }

  @Override
  String getName() {
    return this.name
  }

  @Override
  SourceDirectorySet getProto() {
    return this.proto
  }

  @Override
  SourceDirectorySet getIncludeProtoDirs() {
    return this.includeProtoDirs
  }

  @Override
  SourceDirectorySet getOutput() {
    return this.output
  }

  @Override
  @SuppressWarnings(["SpaceAroundOperator"])
  String getConfName(String configurationName) {
    return this.name == SourceSet.MAIN_SOURCE_SET_NAME
      ? configurationName
      : "${this.name}${configurationName.capitalize()}"
  }

  @Override
  String getProtobufConfName() {
    return this.getConfName("protobuf")
  }

  @Override
  String getCompileProtoPathConfName() {
    return "_${this.getConfName("compileProtoPath")}"
  }

  @Override
  String getCompileOnlyConfName() {
    return this.getConfName("compileOnly")
  }

  @Override
  String getImplementationConfName() {
    return this.getConfName("implementation")
  }

  @Override
  String getTaskName(@Nullable String action, @Nullable String target) {
    String sourceSetName = this.name == SourceSet.MAIN_SOURCE_SET_NAME ? "" : this.name
    return "${action}${sourceSetName.capitalize()}${target.capitalize()}"
  }

  @Override
  String getExtractProtoTaskName() {
    return this.getTaskName("extract", "proto")
  }

  @Override
  String getExtractIncludeProtoTaskName() {
    return this.getTaskName("extractInclude", "proto")
  }

  @Override
  String getGenerateProtoTaskName() {
    return this.getTaskName("generate", "proto")
  }
}
