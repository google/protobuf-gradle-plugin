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
package com.google.protobuf.gradle;

import groovy.transform.CompileStatic;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;

/**
 * Interface exposing the file copying feature. Actual implementations may use the
 * {@link org.gradle.api.file.FileSystemOperations} if available (Gradle 6.0+) or {@link org.gradle.api.Project#copy} if
 * the version of Gradle is below 6.0.
 */
@CompileStatic
interface CopyActionFacade {
    WorkResult copy(Action<? super CopySpec> var1);
    WorkResult delete(Action<? super DeleteSpec> action);
    WorkResult sync(Action<? super CopySpec> var1);

    @CompileStatic
    final class Loader {
        public static CopyActionFacade create(Project project, ObjectFactory objectFactory) {
            if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) >= 0) {
                // Use object factory to instantiate as that will inject the necessary service.
                return objectFactory.newInstance(CopyActionFacade.FileSystemOperationsBased.class);
            }
            return new CopyActionFacade.ProjectBased(project);
        }
    }

    @CompileStatic
    class ProjectBased implements CopyActionFacade {
        private final Project project;

        public ProjectBased(Project project) {
            this.project = project;
        }

        @Override
        public WorkResult copy(Action<? super CopySpec> action) {
            return project.copy(action);
        }

        @Override
        public WorkResult delete(Action<? super DeleteSpec> action) {
            return project.delete(action);
        }

        @Override
        public WorkResult sync(Action<? super CopySpec> action) {
            return project.sync(action);
        }
    }

    @CompileStatic
    abstract class FileSystemOperationsBased implements CopyActionFacade {
        @Inject
        public abstract FileSystemOperations getFileSystemOperations();

        @Override
        public WorkResult copy(Action<? super CopySpec> action) {
            return getFileSystemOperations().copy(action);
        }

        @Override
        public WorkResult delete(Action<? super DeleteSpec> action) {
            return getFileSystemOperations().delete(action);
        }

        @Override
        public WorkResult sync(Action<? super CopySpec> action) {
            return getFileSystemOperations().sync(action);
        }
    }
}
