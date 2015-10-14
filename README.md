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
The latest version is ``0.7.0``. It is available on Maven Central. To add
dependency to it:
```gradle
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.7.0'
  }
}
```

Latest changes are included in the SNAPSHOT artifact:
```gradle
buildscript {
  repositories {
    maven {
       url 'https://oss.sonatype.org/content/repositories/snapshots/'
    }
  }
  dependencies {
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.7.1-SNAPSHOT'
  }
}
```

However, the availability and freshness of the SNAPSHOT artifact are not
guaranteed. You can instead download the source and build it with ``./gradlew
install``, then:
```gradle
buildscript {
  repositories {
    mavenLocal()
  }
  dependencies {
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.7.1-SNAPSHOT'
  }
}
```

## Usage

### Adding the plugin to your project

In Java projects, you must apply the java plugin before applying the Protobuf
plugin:

```gradle
apply plugin: 'java'
apply plugin: 'com.google.protobuf'
```

In Android projects, you must apply the Android plugin first.

```gradle
apply plugin: 'com.android.application'  // or 'com.android.library'
apply plugin: 'com.google.protobuf'
```

### Configuring Protobuf compilation
The Protobuf plugin assumes Protobuf files (``*.proto``) are organized in the
same way as Java source files, in _sourceSets_. The Protobuf files of a
_sourceSet_ (or _variant_ in an Android project) are compiled in a single
``protoc`` run, and the generated files are added to the input of the Java
compilation run of that _sourceSet_ (or _variant_).

#### Cutomizing source directories
The plugin adds a new sources block named ``proto`` alongside ``java`` to every
sourceSet. By default, it includes all ``*.proto`` files under
``src/$sourceSetName/proto``. You can customize it in the same way as you would
custmoize the ``java`` sources.

For Java projects, use the top-level ``sourceSet``:

```gradle
sourceSets {
  main {
    proto {
      // In addition to the default 'src/main/proto'
      srcDir 'src/main/protobuf'
      srcDir 'src/main/protocolbuffers'
      // In addition to the default '**/*.proto' (use with caution).
      // Using an extension other than 'proto' is NOT recommended,
      // because when proto files are published along with class files, we can
      // only tell the type of a file from its extension.
      include '**/*.protodevel'
    }
    java {
      ...
    }
  }
  test {
    proto {
      // In addition to the default 'src/test/proto'
      srcDir 'src/test/protocolbuffers'
    }
  }
}
```

For Android projects, use ``android.sourceSets``:

```gradle
android {
  sourceSets {
    main {
      proto {
        ...
      }
      java {
        ...
      }
    }
  }
}
```

#### Customizing Protobuf compilation
The plugin adds a ``protobuf`` block to the project. It provides all the
configuration knobs.


##### Locate external executables

By default the plugin will search for the ``protoc`` executable in the system
search path. We recommend you to take the advantage of pre-compiled ``protoc``
that we have published on Maven Central:

```gradle
protobuf {
  ...
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = 'com.google.protobuf:protoc:3.0.0-alpha-3'
  }
  ...
}
```

You may also specify a local path.
```gradle
protobuf {
  ...
  protoc {
    path = '/usr/local/bin/protoc'
  }
  ...
}
```

Mulitple assignments are allowed in the ``protoc`` block. The last one wins.

You may also run ``protoc`` with codegen plugins. You need to define all the
codegen plugins you will use in the ``plugins`` block, by specifying the
downloadable artifact or a local path, in the same syntax as in the ``protoc``
block above. This will __not__ apply the plugins. You need to configure the
tasks in the ``generateProtoTasks`` block introduced below to apply the plugins
defined here.

```gradle
protobuf {
  ...
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
  ...
}
```

##### Customize code generation tasks

The Protobuf plugin generates a task for each ``protoc`` run, which is for a
sourceSet in a Java project, or a variant in an Android project. The task has
configuration interfaces that allow you to control the type of outputs, the
codegen plugins to use, and parameters.

You must configure these tasks in the ``generateProtoTasks`` block, which
provides you helper functions to conveniently access tasks that are tied to a
certain build element, and also ensures you configuration will be picked up
correctly by the plugin.

DONOTs:
 - DO NOT assume the names of the tasks, as they may change.
 - DO NOT configure the tasks outside of the ``generateProtoTasks`` block,
   because there are subtle timing constraints on when the tasks should be
   configured.

```gradle
protobuf {
  ...
  generateProtoTasks {
    // all() returns the collection of all protoc tasks
    all().each { task ->
      // Here you can configure the task
    }

    // In addition to all(), you may get the task collection by various
    // criteria:

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
```

Here is how to control ``protoc`` built-in outputs in a closure passed to
``builtins``, which configures a ``NamedDomainObjectContainer``.

```gradle
{ task ->
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
}
```

If you want to remove the built-in output that is automatically added, use
``remove`` method of ``NamedDomainObjectContainer``. For example, to generate
``javanano`` instead of ``java`` in a Java project:
```gradle
{ task ->
  task.builtins {
    remove java
    javanano { }
  }
}
```

Here is how you apply codegen plugins that have been defined in the
``protobuf.plugins`` block introduced above.

```gradle
{ task ->
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
```

The task also provides following options:
```gradle
{ task ->
  // If true, will generate a descriptor_set.desc file under
  // $generatedFilesBaseDir/$sourceSet. Default is false.
  // See --descriptor_set_out in protoc documentation about what it is.
  task.generateDescriptorSet = true

  // Allows to override the default for the descriptor set location
  task.descriptorSetOptions.path = 
    "${projectDir}/build/descriptors/{$task.sourceSet.name}.dsc"

  // If true, the descriptor set will contain line number information 
  // and comments. Default is false.
  task.descriptorSetOptions.includeSourceInfo = true

  // If true, the descriptor set will contain all transitive imports and 
  // is therefore self-contained. Default is false.
  task.descriptorSetOption.includeImports = true
}
```

#### Change where the generated files are

By default generated Java files are under
``$generatedFilesBaseDir/$sourceSet/$builtinPluginName``, where
``$generatedFilesBaseDir`` is ``$buildDir/generated/source/proto`` by default,
and is configurable. E.g.,

```gradle
protobuf {
  ...
  generatedFilesBaseDir = "$projectDir/src/generated"
}
```

The subdirectory name, which is by default ``$builtinPluginName``, can also be
changed by setting the ``outputSubDir`` property in the ``builtins`` or
``plugins`` block of a task configuration within ``generateProtoTasks`` block
(see previous section). E.g.,

```gradle
{ task ->
  task.plugins {
    grpc {
      // Write the generated files under
      // "$generatedFilesBaseDir/$sourceSet/grpcjava"
      outputSubDir = 'grpcjava'
    }
  }
}
```

### Protos in dependencies

If a Java project contains proto files, they will be packaged in the jar files
along with the compiled classes. If a ``compile`` configuration has a
dependency on a project or library jar that contains proto files, they will be
added to the ``--proto_path`` flag of the protoc command line, so that they can
be imported in the proto files of the dependent project. The imported proto
files will not be compiled since they have already been compiled in their own
projects. Example:

```gradle
dependencies {
  compile project(':someProjectWithProtos')
  testCompile files("lib/some-testlib-with-protos.jar")
}
```


If there is a project, package or published artifact that contains just protos
files, whose compiled classes are absent, and you want to use these proto files
in your project and compile them, you can add it to ``protobuf`` dependencies.
Example:

```gradle
dependencies {
  protobuf files('lib/protos.tar.gz')
  testProtobuf 'com.example:published-protos:1.0.0'
}
```

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
