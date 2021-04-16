import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

buildscript {
  repositories {
    gradlePluginPortal()
  }
}

plugins {
  java
  id("java-library")
  id("com.google.protobuf")
}

repositories {
  mavenCentral()
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val protobufDepVersion = "3.0.0"
val grpcVersion = "1.37.0"

dependencies {
  implementation("io.grpc:grpc-protobuf:$grpcVersion")
  implementation("io.grpc:grpc-stub:$grpcVersion")
  implementation("com.google.protobuf:protobuf-java:$protobufDepVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:$protobufDepVersion"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
    }
  }
}

tasks {
  test {
    useJUnitPlatform {
    }
  }
}
