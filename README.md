# Protobuf Plugin for Gradle
The Protobuf plugin provides protobuf compilation to your project.

## Usage
To use the protobuf plugin, include in your build script:

```groovy
apply plugin: ws.antonov.gradle.plugins.protobuf.ProtobufPlugin

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'ws.antonov.gradle.plugins:gradle-plugin-protobuf:0.4'
    }
}

// Optional - defaults to '/usr/local/bin/protoc'
protocPath = '/usr/local/bin/protoc'
```
