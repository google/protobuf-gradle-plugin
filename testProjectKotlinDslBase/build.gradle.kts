import com.google.protobuf.gradle.*
import org.gradle.api.internal.HasConvention
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

plugins {
    java
    idea
    id("com.google.protobuf")
}

repositories {
    maven("https://plugins.gradle.org/m2/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    val grpc = create("grpc") {
    }

    getByName("test") {
        compileClasspath += grpc.output
        runtimeClasspath += grpc.output
    }
}
val grpcImplementation = configurations.getByName("grpcImplementation")

val protobufDep = "com.google.protobuf:protobuf-java:3.0.0"

dependencies {
    protobuf(files("lib/protos.tar.gz"))
    protobuf(files("ext/"))
    testProtobuf(files("lib/protos-test.tar.gz"))

    implementation(protobufDep)
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    testImplementation("junit:junit:4.12")
    // KotlinFooTest.kt requires reflection utilities
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.2.0")
    grpcImplementation(protobufDep)
    grpcImplementation("io.grpc:grpc-stub:1.0.0-pre2")
    grpcImplementation("io.grpc:grpc-protobuf:1.0.0-pre2")
    grpcImplementation("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.0.0"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.0.0-pre2"
        }
    }
    generateProtoTasks {
        ofSourceSet("grpc").forEach { task ->
            task.spec {
                plugins {
                    id("grpc") {
                        outputSubDir = "grpc_output"
                    }
                }
                generateDescriptorSet = true
            }
        }
    }
}

tasks {

    "jar"(Jar::class) {
        sourceSets.forEach { sourceSet ->
            from(sourceSet.output)

            val compileTaskName = sourceSet.getCompileTaskName("java")
            dependsOn(project.tasks.getByName(compileTaskName))
        }

        // gradle 7+ requires a duplicate strategy to be explicitly defined
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    "test"{

        doLast{
            val generateProtoTasks = project.protobuf.generateProtoTasks

            val generateProtoTaskNames = generateProtoTasks.all().map { it.name }.toSet()
            val generateProtoTaskNamesMain = generateProtoTasks.ofSourceSet("main").map { it.name }.toSet()

            assert(setOf("generateProto", "generateGrpcProto", "generateTestProto") == generateProtoTaskNames)
            assert(setOf("generateProto") == generateProtoTaskNamesMain)

            assertJavaCompileHasProtoGeneratedDir("main", listOf("java"))
            assertJavaCompileHasProtoGeneratedDir("test", listOf("java"))
            assertJavaCompileHasProtoGeneratedDir("grpc", listOf("java", "grpc_output"))

            listOf("main", "test").forEach { sourceSet ->
                assertFileExists(false, "$buildDir/generated/source/proto/$sourceSet/descriptor_set.desc")
            }
            assertFileExists(true, "$buildDir/generated/source/proto/grpc/descriptor_set.desc")
        }
    }
}

fun assertJavaCompileHasProtoGeneratedDir(sourceSet: String, codegenPlugins: Collection<String>) {
    val compileJavaTask = tasks.getByName(sourceSets.getByName(sourceSet).getCompileTaskName("java")) as JavaCompile
    assertJavaCompileHasProtoGeneratedDir(project, sourceSet, compileJavaTask, codegenPlugins)
}

fun assertFileExists(exists: Boolean, path: String) {
    if (exists) {
        assert(File(path).exists())
    } else {
        assert(!File(path).exists())
    }
}

fun assertJavaCompileHasProtoGeneratedDir(
    project: Project,
    sourceSet: String,
    compileJavaTask: JavaCompile,
    codegenPlugins: Collection<String>
) {
    val baseDir = File("${project.buildDir}/generated/source/proto/$sourceSet")
    // The expected direct subdirectories under baseDir
    val expectedDirs = codegenPlugins.map { codegenPlugin ->
        File("${project.buildDir}/generated/source/proto/$sourceSet/$codegenPlugin")
    }.toSet()

    val actualDirs = mutableSetOf<File>()
    compileJavaTask.source.visit {

        // If the visited file is or is under a direct subdirectory of baseDir, add
        // that subdirectory to actualDirs.
        var file = this@visit.file
        while (true) {
            if (file.parentFile == baseDir) {
                actualDirs.add(file)
            }
            if (file.parentFile == null) {
                break
            }
            file = file.parentFile
        }
    }
    assert(expectedDirs == actualDirs)
}

