# Protobuf Plugin for Gradle
The Gradle plugin that compiles Protocol Buffer (aka. Protobuf) definition
files (``*.proto``) in your project. The most important functionality of this
plugin is assemble the Protobuf Compiler (``protoc``) command line and run it.
For more information about the Protobuf Compiler, please refer to
[Google Developers Site](https://developers.google.com/protocol-buffers/docs/reference/java-generated?csw=1).

## Latest Version
``com.google.protobuf:protobuf-gradle-plugin:0.5.0-SNAPSHOT``

It has not been released yet. You can build it and install locally by running
``./gradlew install``.

## Usage
To use the protobuf plugin, include in your build script:

```gradle

// For Java project, you must apply the java plugin first.
apply plugin: 'java'

// For Android project, apply the Android plugin first.
apply plugin: 'com.android.application'
// Or:
apply plugin: 'com.android.library'


// Then apply the Protobuf plugin
apply plugin: 'com.google.protobuf'

buildscript {
  repositories {
    mavenLocal()
  }
  dependencies {
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.5.0-SNAPSHOT'
  }
}

// Following are optional configurations.

// For Java projects:
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
    }
  }
  test {
    proto {
      // In addition to the default 'src/test/proto'
      srcDir 'src/test/protocolbuffers'
    }
  }
}

// For Android projects, use android.sourceSets instead of the top-level
// sourceSets.
android {
  sourceSets {
    main {
      proto {
        ...
      }
    }
  }
}

// The Protobuf configuration block.
protobuf {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = 'com.google.protobuf:protoc:3.0.0-alpha-3'
    // or specify a local path. The last one wins.
    path = '/usr/local/bin/protoc'
  }
  // Configure the codegen plugins
  plugins {
    // Define a plugin with name 'grpc'
    grpc {
      artifact = 'io.grpc:protoc-gen-grpc-java:0.1.0-SNAPSHOT'
      // or
      path = 'tools/protoc-gen-grpc-java'
    }
    xrpc {
      path = 'tools/protoc-gen-xrpc'
    }
    // Any other plugins
    ...
  }
  // Customize the location of generated files.
  // Its default value is $buildDir/generated/source/proto
  // Generated files are under $generatedFilesBaseDir/$sourceSet/$pluginName
  generatedFilesBaseDir = "$projectDir/src/generated"

  // The Protobuf plugin creates a task per sourceSet in a Java project, or per
  // variant in an Android project. A task runs protoc to compile the *.proto
  // files of that sourceSet/variant, in the following closure you can customize
  // the protoc invocations through these tasks.
  // This closure is run after all tasks have been generated. Helper functions
  // are provided to the closure to conveniently access tasks that are tied to a
  // certain build element.
  // DO NOT assume the names of the tasks, as they may change.
  // DO NOT configure the tasks outside of this block, because there are subtle
  // timing constraints on when the tasks should be configured.
  generateProtoTasks {
    // all() returns the collection of all protoc tasks
    all().each { task ->
      // Configure built-in outputs. Each block generates a
      // '--<name>_out' flag to the protoc command line.
      task.builtins {
        // In Java projects, the "java" output is added automatically.
        // You only need it if you want it in an Android project or want to add
        // options.
        // DO NOT omit the braces if you want this builtin to be added.
        java { }
        // In Android projects, the "javanano" output is added automatically.
        // You only need it if you want it in an Java project or want to add
        // options.
        javanano {
          // Options added to --javanano_out
          option 'java_multiple_files=true'
          option 'ignore_services=true'
        }
        // Any other builtins
        ...
      }
      // Configure codegen plugins. Each block generates two flags
      // to the protoc command line:
      //  - '--plugin=protoc-gen-<name>:<plugin-path>', and
      //  - '--<name>_out=<output-dir>
      // <name> must have been defined in the protobuf.plugins block
      task.plugins {
        // Use the "grpc" plugin in this task.
        grpc {
          // Options added to --grpc_out
          option 'nano=true'
        }
        // Use the "xrpc" plugin, with no options (braces cannot be omitted)
        xrpc { }
        // Any other plugins
      }
    }

    // (Java only) returns tasks for a sourceSet
    ofSourceSet('main')
    // (Android only) returns tasks for a flavor
    ofFlavor('demo')
    // (Android only) returns tasks for a buildType
    ofBuildType('release')
    // (Android only) returns tasks for a variant
    ofVariant('demoRelease')
    // (Android only) returns non-androidTest tasks
    ofNonTest()
    // (Android only) return androidTest tasks
    ofTest()
  }
}

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
``.proto`` files. They also serve as usage examples. Because the tests include
an Android project, you need to install [Android SDK
Tools](https://developer.android.com/sdk/index.html#Other).

After you made any change to the plugin, be sure to run these tests.
```
$ ./gradlew install && ./gradlew clean test && ./gradlew test
```
The tests use the plugin installed in Maven local repo, so you must install
it before testing it. We cannot make the tests depend the plugin project
directly, because the test projects apply the plugin at evaluation time. At
evaluation time the plugin project has not been compiled yet. The second test
run is to make sure incremental build works.
