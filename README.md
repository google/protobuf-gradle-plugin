# Protobuf Plugin for Gradle
The Protobuf plugin provides protobuf compilation to your project.

## Latest Version
ws.antonov.gradle.plugins:gradle-plugin-protobuf:0.9.1 - Available on Maven Central

## Usage
To use the protobuf plugin, include in your build script:

```groovy
apply plugin: 'protobuf'

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'ws.antonov.gradle.plugins:gradle-plugin-protobuf:0.9.1'
    }
}

// Optional - defaults to 'protoc' searching through your PATH
protocPath = '/usr/local/bin/protoc'
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

dependencies {
    // If you have your protos archived in a tar file, you can specify that as a dependency
    //   ... alternative archive types supported are: jar, tar, tar.gz, tar.bz2, zip
    protobuf files("lib/protos.tar.gz")
    // Different configuration fileSets are supported
    testProtobuf files("lib/protos.tar")
}
```
