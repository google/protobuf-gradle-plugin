package com.google.protobuf.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProtobufModelPluginTest extends Specification {
  @Rule
  final TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')

    def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
    if (pluginClasspathResource == null) {
      throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
    }

    def pluginClasspath = pluginClasspathResource.readLines()
        .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
        .collect { "'$it'" }
        .join(", ")

    // Add the logic under test to the test build
    buildFile << """
          buildscript {
            dependencies {
              classpath files($pluginClasspath)
            }
          }
        """

    // Generate some useful path
    def userDir = System.getProperty("user.dir").replace("\\", "\\\\")
    def vendorDir = "$userDir/vendor"
    def vendorBinaryDir = "$vendorDir/build/binaries"

    // Check to see if the vendor binaries were generated prior to running the test
    assert new File("$vendorBinaryDir/protocExecutable").exists()
    assert new File("$vendorBinaryDir/protobufStaticLibrary").exists()
    assert new File("$vendorBinaryDir/protobufLiteStaticLibrary").exists()

    buildFile << """
          apply plugin: 'cpp'

          // Repositories doesn't seems to be working for this.
          binaries.all {
            if (toolChain instanceof VisualCpp) {
              cppCompiler.args "/I$vendorDir/protobuf/src"
              linker.args "/LIBPATH:$vendorBinaryDir/protobufStaticLibrary", "protobuf.lib"
              linker.args "/LIBPATH:$vendorBinaryDir/protobufLiteStaticLibrary", "protobufLite.lib"
            } else {
              cppCompiler.args "-I$vendorDir/protobuf/src"
              linker.args "-L$vendorBinaryDir/protobufStaticLibrary", "-lprotobuf"
              linker.args "-L$vendorBinaryDir/protobufLiteStaticLibrary", "-lprotobufLite"
            }
          }

          import com.google.protobuf.gradle.*
          apply plugin: 'com.google.protobuf'

          model {
            protobuf {
              protoc {
                path = "$vendorBinaryDir/protocExecutable/protoc"
              }
            }
          }
        """

    testProjectDir.newFolder('src', 'main', 'cpp')
    testProjectDir.newFolder("src", "main", "proto")
  }

  def "a default protobuf source set exists for configuration"() {
    given: "a native executable with proto source set configuration"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec)
            }
          }
        """

    when: "tasks is invoked"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('tasks')
        .build()

    then: "it succeed"
    result.task(":tasks").outcome == TaskOutcome.SUCCESS
  }

  def "it doesn't execute the protobuf source generation task if source set is empty"() {
    given: "a native executable"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec)
            }
          }
        """

    and: "a main c++ source file"
    testProjectDir.newFile('src/main/cpp/main.cc').text = """
          int main() {
            return 0;
          }
        """

    and: "no protobuf files"
    // no proto files

    when: "main executable is built"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('mainExecutable')
        .build()

    then: "the generate task is not ran"
    result.task(":generateMainExecutableMainProto") == null
  }

  def "can create additional protobuf source set and configure them"() {
    given: "a native executable with an additional proto source set"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec) {
                sources {
                  anotherProto(ProtobufSourceSet) {
                    source.include "*.proto"
                    generatedSourceSet.withType(CppSourceSet) {
                      cpp.lib it
                    }
                  }
                }
              }
            }
          }
        """

    when: "tasks is invoked"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('tasks')
        .build()

    then: "it succeed"
    result.task(":tasks").outcome == TaskOutcome.SUCCESS
  }

  def "it execute the protobuf source generation task if source set is not empty"() {
    given: "a native executable"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec)
            }
          }
        """

    and: "a protobuf file"
    testProjectDir.newFile("src/main/proto/Test.proto").text = """
          message TestMessage {
            optional int32 exit_value = 1 [default = 42];
          }
        """

    and: "a c++ source file not referencing the protobuf"
    testProjectDir.newFile("src/main/cpp/main.cc").text = """
          int main() {
            return 0;
          }
        """

    when: "main executable is built"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('mainExecutable')
        .build()

    then: "the generate task is successfully invoked"
    result.task(":generateMainExecutableMainProto").outcome == TaskOutcome.SUCCESS
  }

  def "it can use the protobuf generated code in the application"() {
    given: "a native executable"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec)
            }
          }
        """

    and: "a protobuf file"
    testProjectDir.newFile("src/main/proto/Test.proto").text = """
          message TestMessage {
            optional int32 exit_value = 1 [default = 42];
          }
        """

    and: "a c++ source file referencing the protobuf"
    testProjectDir.newFile("src/main/cpp/main.cc").text = """
          #include "Test.pb.h"
          int main() {
            TestMessage m;
            return m.exit_value();
          }
        """

    when: "main executable is built"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('mainExecutable')
        .build()

    then: "the generate task is successfully invoked"
    result.task(":generateMainExecutableMainProto").outcome == TaskOutcome.SUCCESS
  }

  def "it can use protobuf generated code from multiple source set in the application"() {
    given: "a native executable with multiple protobuf source set"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec) {
                sources {
                  anotherProto(ProtobufSourceSet) {
                    generatedSourceSet.withType(CppSourceSet) {
                      cpp.lib it
                    }
                  }
                }
              }
            }
          }
        """

    and: "protobuf files across multiple source set"
    testProjectDir.newFile("src/main/proto/Test.proto").text = """
          message TestMessage {
            optional int32 exit_value = 1 [default = 42];
          }
        """
    testProjectDir.newFolder("src", "main", "anotherProto", "foo")
    testProjectDir.newFile("src/main/anotherProto/foo/Test.proto").text = """
          package foo;
          message AnotherTestMessage {
            optional int32 value = 1 [default = 42];
          }
        """

    and: "a c++ source file referencing the protobuf messages"
    testProjectDir.newFile("src/main/cpp/main.cc").text = """
          #include "Test.pb.h"
          #include "foo/Test.pb.h"
          int main() {
            TestMessage m1;
            foo::AnotherTestMessage m2;
            return m1.exit_value() + m2.value();
          }
        """

    when: "main executable is built"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('mainExecutable')
        .build()

    then: "the generate tasks are successfully invoked"
    result.task(":generateMainExecutableMainProto").outcome == TaskOutcome.SUCCESS
    result.task(":generateMainExecutableMainAnotherProto").outcome == TaskOutcome.SUCCESS
  }

  def "it can have same include from different source set without conflict at compile time"() {
    given: "a native executable with additional cpp and proto source set"
    buildFile << """
          model {
            components {
              main(NativeExecutableSpec) {
                sources {
                  anotherCpp(CppSourceSet)
                  anotherProto(ProtobufSourceSet) {
                    generatedSourceSet.withType(CppSourceSet) {
                      anotherCpp.lib it
                    }
                  }
                }
              }
            }
          }
        """

    and: "protobuf files across multiple source set that end up having the same include file names"
    testProjectDir.newFile("src/main/proto/Bar.proto").text = """
          package bar1;
          message BarMessage {
            optional int32 exit_value = 1 [default = 2];
          }
        """
    testProjectDir.newFolder("src", "main", "anotherProto")
    testProjectDir.newFile("src/main/anotherProto/Bar.proto").text = """
          package bar2;
          message BarMessage {
            optional int32 value = 2 [default = 40];
          }
        """

    and: "a c++ source files including in turn different include file that match by name"
    testProjectDir.newFolder("src", "main", "anotherCpp")
    testProjectDir.newFile("src/main/anotherCpp/foo.cc").text = """
          #include "Bar.pb.h"

          int foo() {
            bar2::BarMessage bar;
            return bar.value();
          }
        """
    testProjectDir.newFile("src/main/cpp/main.cc").text = """
          #include "Bar.pb.h"

          extern int foo();
          int main() {
            int val = foo();
            bar1::BarMessage bar;

            return val + bar.exit_value();
          }
        """

    when: "main executable is built"
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('mainExecutable')
        .build()

    then: "generate task succeed"
    result.task(":generateMainExecutableMainProto").outcome == TaskOutcome.SUCCESS
    result.task(":generateMainExecutableMainAnotherProto").outcome == TaskOutcome.SUCCESS
  }
}