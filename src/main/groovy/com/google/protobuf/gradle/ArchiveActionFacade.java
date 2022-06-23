/*
 * Copyright (c) 2020, Google Inc. All rights reserved.
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
package com.google.protobuf.gradle;

import groovy.transform.CompileStatic;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;

import javax.inject.Inject;

@CompileStatic
public interface ArchiveActionFacade {

    FileTree zipTree(Object path);

    FileTree tarTree(Object path);

    @CompileStatic
    class ProjectBased implements ArchiveActionFacade {

        private final Project project;

        ProjectBased(Project project) {
            this.project = project;
        }

        @Override
        public FileTree zipTree(Object path) {
            return project.zipTree(path);
        }

        @Override
        public FileTree tarTree(Object path) {
            return project.tarTree(path);
        }
    }

    @CompileStatic
    abstract class ServiceBased implements ArchiveActionFacade {

        // TODO Use public ArchiveOperations from Gradle 6.6 instead
        @Inject
        public abstract FileOperations getFileOperations();

        @Override
        public FileTree zipTree(Object path) {
            return getFileOperations().zipTree(path);
        }

        @Override
        public FileTree tarTree(Object path) {
            return getFileOperations().tarTree(path);
        }
    }
}
