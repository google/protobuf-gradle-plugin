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

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project

@CompileStatic
class ProjectExt {
  private ProjectExt() {
  }

  @SuppressWarnings(["CouldBeSwitchStatement"]) // `if` is better than fallthrough `switch`
  static void forEachVariant(final Project project, final Action<? extends BaseVariant> action) {
    BaseExtension android = project.extensions.getByName("android") as BaseExtension
    project.logger.debug("$project has '$android'")

    if (android instanceof AppExtension) {
      (android as AppExtension).getApplicationVariants().all(action)
    }

    if (android instanceof LibraryExtension) {
      (android as LibraryExtension).getLibraryVariants().all(action)
    }

    if (android instanceof TestExtension) {
      (android as TestExtension).getApplicationVariants().all(action)
    }

    if (android instanceof TestedExtension) {
      (android as TestedExtension).getTestVariants().all(action)
      (android as TestedExtension).getUnitTestVariants().all(action)
    }
  }

  static boolean isAgpAbove422(Project project) {
    // Different type between agp 4.2.2 and 7.0.0
    // androidComponents exists since agp 4.2
    Object androidComponents = project.extensions.findByName("androidComponents")

    // Below 4.2 androidComponents extension does not exists
    // Below 7.0.0 androidComponents extension does not have pluginVersion field
    return androidComponents != null && androidComponents.hasProperty("pluginVersion")
  }
}
