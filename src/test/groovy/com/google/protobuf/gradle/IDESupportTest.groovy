/*
 * Copyright (c) 2017, Google Inc. All rights reserved.
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
package com.google.protobuf.gradle

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests to check ide metadata generation
 */
@CompileDynamic
class IDESupportTest extends Specification {
  // Current supported version is Gradle 5+.
  private static final List<String> GRADLE_VERSIONS = ["5.6", "6.0", "6.7.1", "7.4.2"]

  @Unroll
  void "testProject proto and generated output directories should be added to intellij [gradle #gradleVersion]"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testProject')
      .copyDirs('testProjectBase', 'testProject')
      .build()

    when: "idea is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('idea')
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":idea").outcome == TaskOutcome.SUCCESS
    Node imlRoot = new XmlParser().parse(projectDir.toPath().resolve("testProject.iml").toFile())
    Collection rootMgr = imlRoot.component.findAll { it.'@name' == 'NewModuleRootManager' }
    assert rootMgr.size() == 1
    assert rootMgr.content.sourceFolder.size() == 1

    Set<String> sourceDir = [] as Set
    Set<String> testSourceDir = [] as Set
    Set<String> generatedDirs = [] as Set
    rootMgr.content.sourceFolder[0].each {
      if (Boolean.parseBoolean(it.@isTestSource)) {
        testSourceDir.add(it.@url)
      } else {
        sourceDir.add(it.@url)
      }
      if (Boolean.parseBoolean(it.@generated)) {
        generatedDirs.add(it.@url)
      }
    }

    Set<String> expectedSourceDir = [
      'file://$MODULE_DIR$/src/main/java',
      'file://$MODULE_DIR$/src/grpc/proto',
      'file://$MODULE_DIR$/src/main/proto',
      'file://$MODULE_DIR$/build/extracted-include-protos/grpc',
      'file://$MODULE_DIR$/build/extracted-protos/main',
      'file://$MODULE_DIR$/build/extracted-include-protos/main',
      'file://$MODULE_DIR$/build/extracted-protos/grpc',
      'file://$MODULE_DIR$/build/generated/sources/proto/grpc/java',
      'file://$MODULE_DIR$/build/generated/sources/proto/grpc/grpc_output',
      'file://$MODULE_DIR$/build/generated/sources/proto/main/java',
    ]
    Set<String> expectedTestSourceDir = [
      'file://$MODULE_DIR$/src/test/java',
      'file://$MODULE_DIR$/src/test/proto',
      'file://$MODULE_DIR$/build/extracted-protos/test',
      'file://$MODULE_DIR$/build/extracted-include-protos/test',
      'file://$MODULE_DIR$/build/generated/sources/proto/test/java',
    ]
    Set<String> expectedGeneratedDirs = [
      'file://$MODULE_DIR$/build/extracted-include-protos/grpc',
      'file://$MODULE_DIR$/build/extracted-protos/main',
      'file://$MODULE_DIR$/build/extracted-include-protos/main',
      'file://$MODULE_DIR$/build/extracted-protos/grpc',
      'file://$MODULE_DIR$/build/generated/sources/proto/grpc/java',
      'file://$MODULE_DIR$/build/generated/sources/proto/grpc/grpc_output',
      'file://$MODULE_DIR$/build/generated/sources/proto/main/java',
      'file://$MODULE_DIR$/build/extracted-protos/test',
      'file://$MODULE_DIR$/build/extracted-include-protos/test',
      'file://$MODULE_DIR$/build/generated/sources/proto/test/java',
    ]
    assert Objects.equals(expectedSourceDir, sourceDir)
    assert Objects.equals(expectedTestSourceDir, testSourceDir)
    Objects.equals(expectedGeneratedDirs, generatedDirs)

    where:
    gradleVersion << GRADLE_VERSIONS
  }

  @Unroll
  void "testProject proto and generated output directories should be added to Eclipse [gradle #gradleVersion]"() {
    given: "project from testProject"
    File projectDir = ProtobufPluginTestHelper.projectBuilder('testEclipse')
      .copyDirs('testProjectBase', 'testProject')
      .build()

    when: "eclipse is invoked"
    BuildResult result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments('eclipse')
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
      .build()

    then: "it succeed"
    result.task(":eclipse").outcome == TaskOutcome.SUCCESS
    Node classpathFile = new XmlParser().parse(projectDir.toPath().resolve(".classpath").toFile())
    Collection srcEntries = classpathFile.classpathentry.findAll { it.'@kind' == 'src' }
    assert srcEntries.size() == 6

    Set<String> sourceDir = [] as Set
    srcEntries.each {
      String path = it.@path
      sourceDir.add(path)
      if (path.startsWith("build/generated/sources/proto")) {
        if (path.contains("test")) {
          // test source path has one more attribute: ["test"="true"]
          assert it.attributes.attribute.size() == 3
        } else {
          assert it.attributes.attribute.size() == 2
        }
      }
    }

    Set<String> expectedSourceDir = [
      'src/main/java',
      'src/test/java',
      'build/generated/sources/proto/grpc/java',
      'build/generated/sources/proto/grpc/grpc_output',
      'build/generated/sources/proto/main/java',
      'build/generated/sources/proto/test/java',
    ]
    assert Objects.equals(expectedSourceDir, sourceDir)

    where:
    gradleVersion << GRADLE_VERSIONS
  }
}
