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

import groovy.transform.CompileDynamic
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.api.AndroidSourceSet as DeprecatedAndroidSourceSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer

@CompileDynamic
class AndroidSourceSetFacade {
  private final Object sourceSet

  AndroidSourceSetFacade(Object sourceSet) {
    this.sourceSet = sourceSet
    if (sourceSet instanceof DeprecatedAndroidSourceSet) {
      return
    }
    if (sourceSet instanceof AndroidSourceSet) {
      return
    }
    throw new IllegalArgumentException("source set should be 'com.android.build.api.dsl.AndroidSourceSet' " +
      "or 'com.android.build.gradle.api.AndroidSourceSet', but '${sourceSet.class.packageName}' was present")
  }

  String getName() {
    return this.sourceSet.name
  }

  ExtensionContainer getExtensions() {
    return (this.sourceSet as ExtensionAware).extensions
  }

}
