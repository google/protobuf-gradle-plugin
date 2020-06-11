package com.google.protobuf.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;

import javax.inject.Inject;

public interface ArchiveActionFacade {

    FileTree zipTree(Object path);

    FileTree tarTree(Object path);

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
