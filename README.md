# Protobuf Plugin for Gradle
The Gradle plugin that compiles Protocol Buffer (aka. Protobuf) definition
files (``*.proto``) in your project. There are two pieces of its job:
 1. It assembles the Protobuf Compiler (``protoc``) command line and use it to
    generate Java source files out of your proto files.
 2. It adds the generated Java source files to the input of the corresponding
    Java compilation unit (_sourceSet_ in a Java project; _variant_ in an
    Android project), so that they can be compiled along with your Java sources.

For more information about the Protobuf Compiler, please refer to
[Google Developers Site](https://developers.google.com/protocol-buffers/docs/reference/java-generated?csw=1).

## Latest Version
``com.google.protobuf:protobuf-gradle-plugin:0.4.1`` - Available on Maven Central.

Support for Android projects is coming soon. Check out the [0.5.x dev
branch](https://github.com/google/protobuf-gradle-plugin/tree/v0.5.x)!

## Usage
To use the protobuf plugin, include in your build script:

```groovy

// For Java project, you must apply the java plugin first.
apply plugin: 'java'
// Or, for Android project, apply the Android plugin first.
// apply plugin: 'com.android.application'

apply plugin: 'com.google.protobuf'

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.google.protobuf:protobuf-gradle-plugin:0.4.1'
    }
}

// Optional - specify additional locations of .proto files.
// The default is 'src/<sourceSetName>/proto' {include '**/*.proto'}, where
// <sourceSetName> is typically 'main' and 'test' etc.
sourceSets {
    main {
        proto {
            // In addition to the default 'src/main/proto'
            srcDir 'src/main/protobuf'
            srcDir 'src/main/protocolbuffers'
            // In addition to '**/*.proto'
            include '**/*.protodevel'
            // Optional - configure built-in outputs. Each block generates a
            // '--<name>_out' flag to the command line.
            builtins {
                /*  // 'java' is there by default. Unless you want to add options,
                    // you can omit this block.
                java {
                }
                */
                /*  // To remove the 'java' output
                remove java
                */
                // Adds '--javanano_out'
                javanano {
                    // Options added to --javanano_out
                    option 'java_multiple_files=true'
                    option 'ignore_services=true'
                }
            }
            // Optional - configure codegen plugins. Each block generates two flags
            // to the protoc command line:
            //  - '--plugin=protoc-gen-<name>:<plugin-path>' and
            //  - '--<name>_out=<output-dir>
            // If <name> is defined in protobufCodeGenPlugins, <plugin-path> will be from there.
            // Otherwise, <plugin-path> will be '<projectDir>/protoc-gen-<name>'.
            // <output-dir> is derived from generatedFileDir.
            plugins {
                // Adds --plugin=protoc-gen-grpc:<path> and --grpc_out
                grpc {
                  // Options added to --grpc_out
                  option 'nano=true'
                }
                // Without options. DO NOT omit the braces. Otherwise the
                // plugin won't be added.
                xrpc { }
            }
        }
    }
    test {
        proto {
            // In addition to the default 'src/test/proto'
            srcDir 'src/test/protocolbuffers'
        }
    }
}

// Optional - defaults to 'protoc' searching through your PATH
protocPath = '/usr/local/bin/protoc'
// Optional - specify a 'protoc' that is downloaded from repositories, this overrides 'protocPath'
protocDep = 'com.google.protobuf:protoc:3.0.0-alpha-2'

// Optional - defaults to value below
extractedProtosDir = "${project.buildDir.path}/extracted-protos"
// Optional - defaults to "${project.buildDir}/generated-sources/${sourceSet.name}"
generatedFileDir = "${projectDir}/src" // This directory will get the current sourceSet.name appended to it. i.e. src/main or src/test

// Optional - defines codegen plugins. Defaults to empty collection => []
//  Each entry is a '<name>:<plugin-path>'
protobufCodeGenPlugins = ['foo:./protoc-gen-foo', 'bar:./protoc-gen-bar']

// Optional - define native codegen plugins pulled from repositories
//  Each entry is a '<name>:<plugin-groupId>:<plugin-artifactId>:<version>'.
//  '<plugin-groupId>:<plugin-artifactId>:<version>' is resolved and downloaded
//  from the repositories. Then this entry is transformed into a
//  'protobufCodeGenPlugins' entry '<name>:<path-to-downloaded-plugin>'.
protobufNativeCodeGenPluginDeps = ["grpc:io.grpc:protoc-gen-grpc-java:0.7.0"]

dependencies {
    // If you have your protos archived in a tar file, you can specify that as a dependency
    //   ... alternative archive types supported are: jar, tar, tar.gz, tar.bz2, zip
    protobuf files("lib/protos.tar.gz")
    // Different configuration fileSets are supported
    testProtobuf files("lib/protos.tar")
}
```

More examples can be found in the test projects (``testProject*/``).

## Pre-compiled ``protoc`` artifacts
This [Maven Central directory](https://repo1.maven.org/maven2/com/google/protobuf/protoc/)
lists pre-compiled ``protoc`` artifacts that can be used by this plugin.

## Testing the plugin
``testProject*`` are testing projects that uses this plugin to compile
``.proto`` files. They also serve as usage examples.

After you made any change to the plugin, be sure to run these tests.
```
$ ./gradlew install && ./gradlew clean test && ./gradlew test
```
The tests use the plugin installed in Maven local repo, so you must install
it before testing it. We cannot make the tests depend the plugin project
directly, because the test projects apply the plugin at evaluation time. At
evaluation time the plugin project has not been compiled yet. The second test
run is to make sure incremental build works.
