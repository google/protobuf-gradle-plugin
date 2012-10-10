# Protobuf Plugin for Gradle
The Protobuf plugin provides protobuf compilation to your project.

## Usage
To use the protobuf plugin, include in your build script:

```groovy
apply plugin: 'protobuf'

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'ws.antonov.gradle.plugins:gradle-plugin-protobuf:0.6'
    }
}

// Optional - defaults to 'protoc' searching through your PATH
protocPath = '/usr/local/bin/protoc'

dependencies {
    // If you have your protos archived in a tar file, you can specify that as a dependency
    //   ... alternative archive types supported are: jar, tar, tar.gz, tar.bz2, zip
    protobuf files("lib/protos.tar.gz")
    // Different configuration fileSets are supported
    testProtobuf files("lib/protos.tar")
}
```
