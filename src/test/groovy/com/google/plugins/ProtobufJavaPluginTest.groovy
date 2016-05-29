package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.apache.commons.io.FileUtils

import spock.lang.Specification

class ProtobufJavaPluginTest extends Specification {

    @Rule
    final TemporaryFolder tempDir = new TemporaryFolder()

    final ProtobufPluginTestHelper helper = new ProtobufPluginTestHelper()

    private Project setupBasicProject() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'
        project.apply plugin: 'com.google.protobuf'
        return project
    }

    def "Applying java and com.google.protobuf adds corresponding task to project"() {
        given: "a basic project with java and com.google.protobuf"
        def project = setupBasicProject()

        when: "project evaluated"
        project.evaluate()

        then: "generate tasks added"
        assert project.tasks.generateProto instanceof GenerateProtoTask
        assert project.tasks.generateTestProto instanceof GenerateProtoTask

        assert project.tasks.extractIncludeProto instanceof ProtobufExtract
        assert project.tasks.extractIncludeTestProto instanceof ProtobufExtract
        assert project.tasks.extractProto instanceof ProtobufExtract
        assert project.tasks.extractTestProto instanceof ProtobufExtract
    }

    def "Custom sourceSet should get its own GenerateProtoTask"() {
        given: "a basic project with java and com.google.protobuf"
        def project = setupBasicProject()

        when: "adding custom sourceSet nano"
        project.sourceSets.create('nano')

        and: "project evaluated"
        project.evaluate()

        then: "tasks for nano added"
        assert project.tasks.generateNanoProto instanceof GenerateProtoTask

        assert project.tasks.extractIncludeNanoProto instanceof ProtobufExtract
        assert project.tasks.extractNanoProto instanceof ProtobufExtract
    }

    def "testProject should be successfully executed"() {
        given: "project from testProject"
        def projectDir = tempDir.newFolder()
        helper.copyTestProject('testProject', projectDir)

        when: "build is invoked"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('build')
            .build()

        then: "it succeed"
        result.task(":build").outcome == TaskOutcome.SUCCESS
        ['grpc', 'grpc_nano', 'main', 'nano', 'test'].each {
            def generatedSrcDir = new File(projectDir.path, "build/generated/source/proto/$it")
            def fileList = []
            generatedSrcDir.eachFileRecurse { file ->
                if (file.path.endsWith('.java')) {
                    fileList.add (file)
                }
            }
            assert fileList.size > 0
        }
    }

    def "testProjectDependent should be successfully executed"() {
        given: "project from testProject & testProjectDependent"
        def mainProjectDir = tempDir.newFolder()
        def settingsFile = new File(mainProjectDir, 'settings.gradle')
        settingsFile.createNewFile()

        ['testProject', 'testProjectDependent'].each {
            helper.copyTestProject(it, new File(mainProjectDir.path, it))
            settingsFile << """
                include ':$it'
                project(':$it').projectDir = "\$rootDir/testProject" as File
            """
        }

        def buildFile = new File(mainProjectDir, 'build.gradle')
        buildFile.createNewFile()

        when: "build is invoked"
        def result = GradleRunner.create()
            .withProjectDir(mainProjectDir)
            .withArguments('testProjectDependent:build')
            .build()

        then: "it succeed"
        result.task(":testProjectDependent:build").outcome == TaskOutcome.SUCCESS
    }

    def "testProjectCustomProtoDir should be successfully executed"() {
        given: "project from testProjectCustomProtoDir"
        def projectDir = tempDir.newFolder()
        helper.copyTestProject('testProjectCustomProtoDir', projectDir)

        when: "build is invoked"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('build')
            .build()

        then: "it succeed"
        result.task(":build").outcome == TaskOutcome.SUCCESS
    }
}