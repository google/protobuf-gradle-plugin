import com.google.protobuf.gradle.*
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

// A minimal example Java project that uses the protobuf plugin.
// To build it:
// $ ../gradlew clean build

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.8.7-SNAPSHOT")
    }

    // ********************************************************************************* //
    // TODO: Remove after snapshot publish
    // We add the root projects output directory as a repo
    // since the snapshot is not published yet.
    repositories {
        flatDir{
            dir("$projectDir/../../build/libs/")
        }
    }
    dependencies {
        // We have to explicitly add the plugin dependencies to the classpath
        // since we are using a local artifact here.
        classpath("com.google.gradle:osdetector-gradle-plugin:1.6.0")
        classpath("com.google.guava:guava:18.0")
        classpath("com.google.gradle:osdetector-gradle-plugin:1.4.0")
        classpath("commons-lang:commons-lang:2.6")
    }
    // ********************************************************************************* //
}

plugins {
    java
    idea
}

apply(plugin = "com.google.protobuf")

repositories {
    maven("https://plugins.gradle.org/m2/" )
}

dependencies {
    compile("com.google.protobuf:protobuf-java:3.6.1")
    compile("io.grpc:grpc-stub:1.15.1")
    compile("io.grpc:grpc-protobuf:1.15.1")
    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        compile("javax.annotation:javax.annotation-api:1.3.1")
    }
    // Extra proto source files besides the ones residing under
    // "src/main".
    protobuf(files("lib/protos.tar.gz"))
    protobuf(fileTree("ext/"))

    testCompile("junit:junit:4.12")
    // Extra proto source files for test besides the ones residing under
    // "src/test".
    testProtobuf(files("lib/protos-test.tar.gz"))
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.15.1"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach{
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without
                // options.  Note the braces cannot be omitted, otherwise the
                // plugin will not be added. This is because of the implicit way
                // NamedDomainObjectContainer binds the methods.
                id("grpc")
            }
        }
    }
}