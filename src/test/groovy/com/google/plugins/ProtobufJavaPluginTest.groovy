package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class ProtobufJavaPluginTest {

    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'
        project.apply plugin: 'com.google.protobuf'
    }

    @Test
    public void generateProtoAddedToProject() {
        project.evaluate()

        assert project.tasks.generateProto instanceof GenerateProtoTask
        assert project.tasks.generateTestProto instanceof GenerateProtoTask

        assert project.tasks.extractIncludeProto instanceof ProtobufExtract
        assert project.tasks.extractIncludeTestProto instanceof ProtobufExtract
        assert project.tasks.extractProto instanceof ProtobufExtract
        assert project.tasks.extractTestProto instanceof ProtobufExtract
    }

    @Test
    public void generateProtoForSourceSetAddedToProject() {
        project.sourceSets.create('nano')

        project.evaluate()

        assert project.tasks.generateNanoProto instanceof GenerateProtoTask

        assert project.tasks.extractIncludeNanoProto instanceof ProtobufExtract
        assert project.tasks.extractNanoProto instanceof ProtobufExtract
    }
}