import com.google.protobuf.gradle.*

plugins {
  id("java")
  id("idea")
  id("com.google.protobuf") version "0.9.4"
}

repositories {
  mavenCentral()
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      useJUnitJupiter("6.0.2")
    }
  }
}

dependencies {
  implementation("com.google.protobuf:protobuf-java:4.33.4")
  implementation("io.grpc:grpc-stub:1.78.0")
  implementation("io.grpc:grpc-protobuf:1.78.0")

  if (JavaVersion.current().isJava9Compatible()) {
    // Workaround for @javax.annotation.Generated
    // see: https://github.com/grpc/grpc-java/issues/3633
    implementation("javax.annotation:javax.annotation-api:1.3.1")
  }

  // Extra proto source files besides the ones residing under
  // "src/main".
  protobuf(files("lib/protos.tar.gz"))
  protobuf(files("ext/"))

  testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.2")

  // Extra proto source files for test besides the ones residing under
  // "src/test".
  testProtobuf(files("lib/protos-test.tar.gz"))
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:4.33.4"
  }
  plugins {
    // Optional: an artifact spec for a protoc plugin, with "grpc" as
    // the identifier, which can be referred to in the "plugins"
    // container of the "generateProtoTasks" closure.
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.78.0"
    }
  }
  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        // Apply the "grpc" plugin whose spec is defined above, without
        // options. Note the braces cannot be omitted, otherwise the
        // plugin will not be added. This is because of the implicit way
        // NamedDomainObjectContainer binds the methods.
        id("grpc") { }
      }
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}
