import com.google.protobuf.gradle.*
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

// A minimal example Java project that uses the protobuf plugin.
// To build it:
// $ ./gradlew clean build
plugins {
    java
    idea
    id("com.google.protobuf") version "0.8.8"
}

repositories {
    maven("https://plugins.gradle.org/m2/")
}

sourceSets{
    create("sample"){
        proto {
            srcDir("src/sample/protobuf")
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.6.1")
    implementation("io.grpc:grpc-stub:1.15.1")
    implementation("io.grpc:grpc-protobuf:1.15.1")
    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        implementation("javax.annotation:javax.annotation-api:1.3.1")
    }
    // Extra proto source files besides the ones residing under
    // "src/main".
    protobuf(files("lib/protos.tar.gz"))
    protobuf(files("ext/"))

    // Adding dependency for configuration from custom sourceSet
    "sampleProtobuf"(files("ext/"))

    testImplementation("junit:junit:4.12")
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
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without options.
                id("grpc")
            }
        }
    }
}
