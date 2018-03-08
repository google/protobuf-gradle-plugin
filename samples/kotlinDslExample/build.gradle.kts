import com.google.protobuf.gradle.*
import groovy.lang.Closure
import groovy.lang.GroovyObject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "gkt"
version = "1.0-SNAPSHOT"

buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.10"
    var grpc_version: String by extra
    grpc_version = "1.8.0"

    repositories {
        jcenter()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", kotlin_version))
    }

}

val kotlin_version: String by extra
var grpc_version: String by extra

plugins {
    application
    java
    idea
    kotlin("jvm") version ("1.2.10")
    id("com.google.protobuf") version ("0.8.3")
}

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib-jdk8", kotlin_version))
    compile("io.grpc:grpc-netty:$grpc_version")
    compile("io.grpc:grpc-protobuf:$grpc_version")
    compile("io.grpc:grpc-stub:$grpc_version")

    testCompile("io.grpc:grpc-testing:$grpc_version")
    testCompile("org.mockito:mockito-core:1.9.5")
    testCompile("junit", "junit", "4.12")
}

configure<ProtobufConvention> {
    protobuf(closureOf<ProtobufConfigurator> {
        generatedFilesBaseDir = "$projectDir/src/generated"
        protoc(closureOf<ExecutableLocator> {
            artifact = "com.google.protobuf:protoc:3.5.0"
        })
        plugins(closureOf<NamedDomainObjectContainer<ExecutableLocator>> {
            create("grpc", closureOf<ExecutableLocator> {
                artifact = "io.grpc:protoc-gen-grpc-java:$grpc_version"
            })
        })
        generateProtoTasks(closureOf<ProtobufConfigurator.JavaGenerateProtoTaskCollection> {
            all().forEach {
                it?.plugins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
                    add(create("grpc"))
                })
            }
        })
    })
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    sourceSets {
        getByName("main") {
            withGroovyBuilder {
                "proto" {
                    "srcDir"("proto")
                }
            }
            java {
                srcDir("src/generated/main/java")
                srcDir("src/generated/main/grpc")
                srcDir("src/main/java")

            }
        }
    }
}

application {
    mainClassName = "HelloWorldServer"
}

idea {
    module {
        sourceDirs.plus(file("$projectDir/src/main/java"))
        sourceDirs.plus(file("$projectDir/src/main/kotlin"))
        sourceDirs.plus(file("$projectDir/src/generated/main/java"))
        sourceDirs.plus(file("$projectDir/src/generated/main/grpc"))
        testSourceDirs.plus(file("$projectDir/src/test/java"))
        testSourceDirs.plus(file("$projectDir/src/test/kotlin"))
    }
}

tasks {

    withType<KotlinCompile> {
        dependsOn("generateProto")
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<Wrapper> {
        gradleVersion = "4.4.1"
        distributionType = Wrapper.DistributionType.ALL
    }

    withType<Delete> {
        delete("$projectDir/src/generated")
    }
}