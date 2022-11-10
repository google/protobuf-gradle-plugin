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
package com.google.protobuf.gradle.internal

import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory

@CompileStatic
class DefaultProtoSourceSet implements ProtoSourceSet {
  private final String name
  private final SourceDirectorySet proto
  private final ConfigurableFileCollection includeProtoDirs
  private final ConfigurableFileCollection output

  DefaultProtoSourceSet(String name, ObjectFactory objects) {
    this.name = name
    this.proto = objects.sourceDirectorySet("proto", "${name.capitalize()} Proto Source")
    this.includeProtoDirs = objects.fileCollection()
    this.output = objects.fileCollection()
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
  ConfigurableFileCollection getIncludeProtoDirs() {
    return this.includeProtoDirs
  }

  @Override
  ConfigurableFileCollection getOutput() {
    return this.output
  }

  @Override
  void includesFrom(ProtoSourceSet protoSourceSet) {
    this.includeProtoDirs.from(protoSourceSet.proto.sourceDirectories)
    this.includeProtoDirs.from(protoSourceSet.includeProtoDirs)
  }

  @Override
  void extendsFrom(ProtoSourceSet protoSourceSet) {
    this.proto.source(protoSourceSet.proto)
    this.includeProtoDirs.from(protoSourceSet.includeProtoDirs)
  }
}
