import com.android.build.gradle.api.BaseVariant
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofBuildType
import com.google.protobuf.gradle.ofFlavor
import com.google.protobuf.gradle.ofNonTest
import com.google.protobuf.gradle.ofVariant
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
  id("com.android.application")
  id("com.google.protobuf")
}

repositories {
  maven("https://maven.google.com")
  maven("https://plugins.gradle.org/m2/")
}

android {
  compileSdkVersion(26)

  defaultConfig {
    applicationId = "io.grpc.helloworldexample"
    minSdkVersion(7)
    targetSdkVersion(23)
    versionCode = 1
    versionName = "1.0"
  }

  flavorDimensions("abi", "version")

  productFlavors {
    create("freeapp") {
      setDimension("version")
    }
    create("retailapp") {
      setDimension("version")
    }
    create("x86") {
      setDimension("abi")
    }
    create("arm") {
      setDimension("abi")
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android.txt"),
          "proguard-rules.pro"
      )
    }
  }

  sourceSets {
    getByName("main") {
      proto {
        srcDir("src/main/protocolbuffers")
      }
    }
    getByName("test") {
      proto {
        srcDir("src/test/protocolbuffers")
      }
    }
    getByName("androidTest") {
      proto {
        srcDir("src/androidTest/protocolbuffers")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
  }

  packagingOptions {
    exclude("io/grpc/testing/integration/empty.proto")
    exclude("io/grpc/testing/integration/test.proto")
    exclude("io/grpc/testing/integration/messages.proto")
    exclude("tmp/stuff.proto")
  }

  // https://github.com/square/okio/issues/58
  lintOptions {
    warning("InvalidPackage")
    isAbortOnError = false
  }

  dexOptions {
    javaMaxHeapSize = "1g"
    threadCount = 1 // reduce predex thread count to limit memory usage
  }
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.0.0"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.0.0-pre2"
    }
    id("javalite") {
      artifact = "com.google.protobuf:protoc-gen-javalite:3.0.0"
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.plugins {
        id("javalite") { }
      }
    }
    ofNonTest().forEach { task ->
      task.plugins {
        id("grpc") {
          // Options added to --grpc_out
          option("lite")
        }
      }
    }
  }
}

dependencies {
  compile("com.android.support:appcompat-v7:23.4.0")
  compile("com.squareup.okhttp:okhttp:2.7.5")
  compile("javax.annotation:javax.annotation-api:1.2")
  compile("com.google.protobuf:protobuf-lite:3.0.0")
  compile("io.grpc:grpc-core:1.0.0-pre2")
  compile("io.grpc:grpc-stub:1.0.0-pre2")
  compile("io.grpc:grpc-okhttp:1.0.0-pre2")
  compile("io.grpc:grpc-protobuf-lite:1.0.0-pre2") {
    // Otherwise Android compile will complain "Multiple dex files define ..."
    exclude(module = "protobuf-lite")
  }
  compile(project(":testProjectLite")) {
    exclude(module = "protobuf-lite")
  }
  protobuf(files("lib/protos.jar"))
  testCompile("junit:junit:4.12")
}

afterEvaluate {
  // 'gradle test' will run the unit tests, which is still experimental in
  // Android plugin, and would do nothing with our setup. We make 'test' to
  // trigger the "androidTest" Java compile tasks.
  val test by tasks.existing
  android.testVariants.forEach { testVariant ->
    test {
      dependsOn(testVariant.getJavaCompileProvider())
    }
  }
  test {
    doLast {
      val genProtoTasks = project.protobuf.protobuf.generateProtoTasks

      val genProtoTaskNames = setOf(
          "generateArmFreeappDebugAndroidTestProto",
          "generateArmFreeappDebugUnitTestProto",
          "generateArmFreeappReleaseUnitTestProto",
          "generateArmFreeappDebugProto",
          "generateArmFreeappReleaseProto",
          "generateArmRetailappDebugAndroidTestProto",
          "generateArmRetailappDebugUnitTestProto",
          "generateArmRetailappReleaseUnitTestProto",
          "generateArmRetailappDebugProto",
          "generateArmRetailappReleaseProto",
          "generateX86FreeappDebugAndroidTestProto",
          "generateX86FreeappDebugUnitTestProto",
          "generateX86FreeappReleaseUnitTestProto",
          "generateX86FreeappDebugProto",
          "generateX86FreeappReleaseProto",
          "generateX86RetailappDebugAndroidTestProto",
          "generateX86RetailappDebugUnitTestProto",
          "generateX86RetailappReleaseUnitTestProto",
          "generateX86RetailappDebugProto",
          "generateX86RetailappReleaseProto")
      assert(genProtoTaskNames == genProtoTasks.all().map { it.name }.toSet())

      val genProtoTaskNamesTests = setOf(
          "generateArmFreeappDebugAndroidTestProto",
          "generateArmFreeappDebugUnitTestProto",
          "generateArmFreeappReleaseUnitTestProto",
          "generateArmRetailappDebugAndroidTestProto",
          "generateArmRetailappDebugUnitTestProto",
          "generateArmRetailappReleaseUnitTestProto",
          "generateX86FreeappDebugAndroidTestProto",
          "generateX86FreeappDebugUnitTestProto",
          "generateX86FreeappReleaseUnitTestProto",
          "generateX86RetailappDebugAndroidTestProto",
          "generateX86RetailappDebugUnitTestProto",
          "generateX86RetailappReleaseUnitTestProto")
      assert(genProtoTaskNamesTests == genProtoTasks.ofNonTest().map { it.name }.toSet())


      val genProtoTaskNamesNonTests = setOf(
          "generateArmFreeappDebugProto",
          "generateArmFreeappReleaseProto",
          "generateArmRetailappDebugProto",
          "generateArmRetailappReleaseProto",
          "generateX86FreeappDebugProto",
          "generateX86FreeappReleaseProto",
          "generateX86RetailappDebugProto",
          "generateX86RetailappReleaseProto")
      assert(genProtoTaskNamesNonTests == genProtoTasks.ofNonTest().map { it.name }.toSet())

      val genProtoTaskNamesFreeApp = setOf(
          "generateArmFreeappDebugAndroidTestProto",
          "generateArmFreeappDebugUnitTestProto",
          "generateArmFreeappReleaseUnitTestProto",
          "generateArmFreeappDebugProto",
          "generateArmFreeappReleaseProto",
          "generateX86FreeappDebugAndroidTestProto",
          "generateX86FreeappDebugUnitTestProto",
          "generateX86FreeappReleaseUnitTestProto",
          "generateX86FreeappDebugProto",
          "generateX86FreeappReleaseProto")
      assert(genProtoTaskNamesFreeApp == genProtoTasks.ofFlavor("freeapp").map { it.name }.toSet())

      val genProtoTaskNamesRetailApp = setOf(
          "generateArmRetailappDebugAndroidTestProto",
          "generateArmRetailappDebugUnitTestProto",
          "generateArmRetailappReleaseUnitTestProto",
          "generateArmRetailappDebugProto",
          "generateArmRetailappReleaseProto",
          "generateX86RetailappDebugAndroidTestProto",
          "generateX86RetailappDebugUnitTestProto",
          "generateX86RetailappReleaseUnitTestProto",
          "generateX86RetailappDebugProto",
          "generateX86RetailappReleaseProto")
      assert(genProtoTaskNamesRetailApp == genProtoTasks.ofFlavor("retailapp").map { it.name }.toSet())

      val genProtoTaskNamesX86 = setOf(
          "generateX86FreeappDebugAndroidTestProto",
          "generateX86FreeappDebugUnitTestProto",
          "generateX86FreeappReleaseUnitTestProto",
          "generateX86FreeappDebugProto",
          "generateX86FreeappReleaseProto",
          "generateX86RetailappDebugAndroidTestProto",
          "generateX86RetailappDebugUnitTestProto",
          "generateX86RetailappReleaseUnitTestProto",
          "generateX86RetailappDebugProto",
          "generateX86RetailappReleaseProto")
      assert(genProtoTaskNamesX86 == genProtoTasks.ofFlavor("x86").map { it.name }.toSet())

      val genProtoTaskNamesArm = setOf(
          "generateArmFreeappDebugAndroidTestProto",
          "generateArmFreeappDebugUnitTestProto",
          "generateArmFreeappReleaseUnitTestProto",
          "generateArmFreeappDebugProto",
          "generateArmFreeappReleaseProto",
          "generateArmRetailappDebugAndroidTestProto",
          "generateArmRetailappDebugUnitTestProto",
          "generateArmRetailappReleaseUnitTestProto",
          "generateArmRetailappDebugProto",
          "generateArmRetailappReleaseProto"
      )
      assert(genProtoTaskNamesArm == genProtoTasks.ofFlavor("arm").map { it.name }.toSet())

      val genProtoTaskNamesDebug = setOf(
          "generateArmFreeappDebugAndroidTestProto",
          "generateArmFreeappDebugUnitTestProto",
          "generateArmFreeappDebugProto",
          "generateArmRetailappDebugAndroidTestProto",
          "generateArmRetailappDebugUnitTestProto",
          "generateArmRetailappDebugProto",
          "generateX86FreeappDebugAndroidTestProto",
          "generateX86FreeappDebugUnitTestProto",
          "generateX86FreeappDebugProto",
          "generateX86RetailappDebugAndroidTestProto",
          "generateX86RetailappDebugUnitTestProto",
          "generateX86RetailappDebugProto")
      assert(genProtoTaskNamesDebug == genProtoTasks.ofBuildType("debug").map { it.name }.toSet())

      val genProtoTaskNamesRelease = setOf(
          "generateArmFreeappReleaseProto",
          "generateArmFreeappReleaseUnitTestProto",
          "generateArmRetailappReleaseProto",
          "generateArmRetailappReleaseUnitTestProto",
          "generateX86FreeappReleaseProto",
          "generateX86FreeappReleaseUnitTestProto",
          "generateX86RetailappReleaseProto",
          "generateX86RetailappReleaseUnitTestProto")
      assert(genProtoTaskNamesRelease == genProtoTasks.ofBuildType("release").map { it.name }.toSet())

      assert(setOf("generateX86FreeappDebugAndroidTestProto") ==
                 genProtoTasks.ofVariant("x86FreeappDebugAndroidTest").map { it.name }.toSet())

      android.applicationVariants.forEach { variant ->
        assertJavaCompileHasProtoGeneratedDir(variant, listOf("javalite", "grpc"))
      }
      android.testVariants.forEach { variant ->
        assertJavaCompileHasProtoGeneratedDir(variant, listOf("javalite"))
      }
    }
  }
}

fun assertJavaCompileHasProtoGeneratedDir(variant: BaseVariant,
                                          codegenPlugins: Collection<String>) {
  assertJavaCompileHasProtoGeneratedDir(project,
                                        variant.name,
                                        variant.javaCompileProvider.get(),
                                        codegenPlugins)
}

fun assertJavaCompileHasProtoGeneratedDir(
    project: Project,
    variant: String,
    compileJavaTask: JavaCompile,
    codegenPlugins: Collection<String>
) {
  val baseDir = File("${project.buildDir}/generated/source/proto/$variant")
  // The expected direct subdirectories under baseDir
  val expectedDirs = codegenPlugins.map { codegenPlugin ->
    File("${project.buildDir}/generated/source/proto/$variant/$codegenPlugin")
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
