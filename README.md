# Protobuf Plugin for Gradle
The Protobuf plugin provides protobuf compilation to your project.

## Latest Version
com.google.protobuf:protobuf-gradle-plugin:0.1.0 - Available on Maven Central

## Usage
To use the protobuf plugin, include in your build script:

```groovy
apply plugin: 'protobuf'

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.google.protobuf:protobuf-gradle-plugin:0.1.0'
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

// Optional - defaults to empty collection => []
//  If entry separated by ':', will translate into 'protoc' argument '--plugin=protoc-gen-${values[0]}=${values[1]}'
//  If entry is anything else, will translate into 'protoc' argument '--plugin=protoc-gen-${it}=${project.projectDir}/protoc-gen-${it}'
//
//  To execute the plugin, you either need to point to the full path, or have an executable shell script in the project main dir
protobufCodeGenPlugins = ['foo:./protoc-gen-foo', 'bar']

// Optional native codegen plugins from repositories
//  Each entry is a '<name>:<plugin-groupId>:<plugin-artifactId>:<version>'.
//  '<plugin-groupId>:<plugin-artifactId>:<version>' is resolved and downloaded
//  from the repositories. Then this entry is transformed into a
//  'protobufCodeGenPlugins' entry '<name>:<path-to-downloaded-plugin>'.
// NOTE: we are still in the process of deploying the GRPC Java codegen to
// Maven Central. Currently there are no read-to-use codegen artifacts.
protobufNativeCodeGenPluginDeps = ["java_plugin:io.grpc:protoc-gen-grpc-java:0.1.0-SNAPSHOT"]

dependencies {
    // If you have your protos archived in a tar file, you can specify that as a dependency
    //   ... alternative archive types supported are: jar, tar, tar.gz, tar.bz2, zip
    protobuf files("lib/protos.tar.gz")
    // Different configuration fileSets are supported
    testProtobuf files("lib/protos.tar")
}
```

## Pre-compiled ``protoc`` artifacts
This [Maven Central directory](https://repo1.maven.org/maven2/com/google/protobuf/protoc/)
lists pre-compiled ``protoc`` artifacts that can be used by this plugin.
