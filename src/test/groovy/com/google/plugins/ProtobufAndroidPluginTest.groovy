package com.google.protobuf.gradle.plugins

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtract
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProtobufAndroidPluginTest extends Specification {

    @Rule
    final TemporaryFolder tempDir = new TemporaryFolder()

    def "testProjectAndroid should be successfully executed"() {
        given: "project from testProject & testProjectAndroid"
        def mainProjectDir = tempDir.newFolder()
        def settingsFile = new File(mainProjectDir, 'settings.gradle')
        settingsFile.createNewFile()

        ['testProject', 'testProjectAndroid'].each {
            ProtobufPluginTestHelper.copyTestProject(it, new File(mainProjectDir.path, it))
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
            .withArguments('testProjectAndroid:build')
            .build()

        then: "it succeed"
        result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS
    }
}