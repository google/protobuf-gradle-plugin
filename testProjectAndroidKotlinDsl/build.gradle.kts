import com.android.build.gradle.api.BaseVariant
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.internal.DefaultGenerateProtoTaskCollection

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
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
    all {
      plugins {
        id("javalite") { }
      }
    }
    ofNonTest {
      plugins {
        id("grpc") {
          // Options added to --grpc_out
          option("lite")
        }
      }
    }
  }
}

dependencies {
  implementation("com.android.support:appcompat-v7:23.4.0")
  implementation("com.squareup.okhttp:okhttp:2.7.5")
  implementation("javax.annotation:javax.annotation-api:1.2")
  implementation("com.google.protobuf:protobuf-lite:3.0.0")
  implementation("io.grpc:grpc-core:1.0.0-pre2")
  implementation("io.grpc:grpc-stub:1.0.0-pre2")
  implementation("io.grpc:grpc-okhttp:1.0.0-pre2")
  implementation("io.grpc:grpc-protobuf-lite:1.0.0-pre2") {
    // Otherwise Android compile will complain "Multiple dex files define ..."
    exclude(module = "protobuf-lite")
  }
  implementation(project(":testProjectLite")) {
    exclude(module = "protobuf-lite")
  }
  protobuf(files("lib/protos.jar"))
  testImplementation("junit:junit:4.12")
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
      val genProtoTasks = project.protobuf.generateProtoTasks as DefaultGenerateProtoTaskCollection

      val genProtoTaskNames = setOf(
          "armFreeappDebugAndroidTest",
          "armFreeappDebugUnitTest",
          "armFreeappReleaseUnitTest",
          "armFreeappDebug",
          "armFreeappRelease",
          "armRetailappDebugAndroidTest",
          "armRetailappDebugUnitTest",
          "armRetailappReleaseUnitTest",
          "armRetailappDebug",
          "armRetailappRelease",
          "x86FreeappDebugAndroidTest",
          "x86FreeappDebugUnitTest",
          "x86FreeappReleaseUnitTest",
          "x86FreeappDebug",
          "x86FreeappRelease",
          "x86RetailappDebugAndroidTest",
          "x86RetailappDebugUnitTest",
          "x86RetailappReleaseUnitTest",
          "x86RetailappDebug",
          "x86RetailappRelease")
      assert(genProtoTaskNames == genProtoTasks.all().map { it.name }.toSet())

      val genProtoTaskNamesTests = setOf(
          "armFreeappDebugAndroidTest",
          "armFreeappDebugUnitTest",
          "armFreeappReleaseUnitTest",
          "armRetailappDebugAndroidTest",
          "armRetailappDebugUnitTest",
          "armRetailappReleaseUnitTest",
          "x86FreeappDebugAndroidTest",
          "x86FreeappDebugUnitTest",
          "x86FreeappReleaseUnitTest",
          "x86RetailappDebugAndroidTest",
          "x86RetailappDebugUnitTest",
          "x86RetailappReleaseUnitTest")
      assert(genProtoTaskNamesTests == genProtoTasks.ofNonTest().map { it.name }.toSet())


      val genProtoTaskNamesNonTests = setOf(
          "armFreeappDebug",
          "armFreeappRelease",
          "armRetailappDebug",
          "armRetailappRelease",
          "x86FreeappDebug",
          "x86FreeappRelease",
          "x86RetailappDebug",
          "x86RetailappRelease")
      assert(genProtoTaskNamesNonTests == genProtoTasks.ofNonTest().map { it.name }.toSet())

      val genProtoTaskNamesFreeApp = setOf(
          "armFreeappDebugAndroidTest",
          "armFreeappDebugUnitTest",
          "armFreeappReleaseUnitTest",
          "armFreeappDebug",
          "armFreeappRelease",
          "x86FreeappDebugAndroidTest",
          "x86FreeappDebugUnitTest",
          "x86FreeappReleaseUnitTest",
          "x86FreeappDebug",
          "x86FreeappRelease")
      assert(genProtoTaskNamesFreeApp == genProtoTasks.ofFlavor("freeapp").map { it.name }.toSet())

      val genProtoTaskNamesRetailApp = setOf(
          "armRetailappDebugAndroidTest",
          "armRetailappDebugUnitTest",
          "armRetailappReleaseUnitTest",
          "armRetailappDebug",
          "armRetailappRelease",
          "x86RetailappDebugAndroidTest",
          "x86RetailappDebugUnitTest",
          "x86RetailappReleaseUnitTest",
          "x86RetailappDebug",
          "x86RetailappRelease")
      assert(genProtoTaskNamesRetailApp == genProtoTasks.ofFlavor("retailapp").map { it.name }.toSet())

      val genProtoTaskNamesX86 = setOf(
          "x86FreeappDebugAndroidTest",
          "x86FreeappDebugUnitTest",
          "x86FreeappReleaseUnitTest",
          "x86FreeappDebug",
          "x86FreeappRelease",
          "x86RetailappDebugAndroidTest",
          "x86RetailappDebugUnitTest",
          "x86RetailappReleaseUnitTest",
          "x86RetailappDebug",
          "x86RetailappRelease")
      assert(genProtoTaskNamesX86 == genProtoTasks.ofFlavor("x86").map { it.name }.toSet())

      val genProtoTaskNamesArm = setOf(
          "armFreeappDebugAndroidTest",
          "armFreeappDebugUnitTest",
          "armFreeappReleaseUnitTest",
          "armFreeappDebug",
          "armFreeappRelease",
          "armRetailappDebugAndroidTest",
          "armRetailappDebugUnitTest",
          "armRetailappReleaseUnitTest",
          "armRetailappDebug",
          "armRetailappRelease"
      )
      assert(genProtoTaskNamesArm == genProtoTasks.ofFlavor("arm").map { it.name }.toSet())

      val genProtoTaskNamesDebug = setOf(
          "armFreeappDebugAndroidTest",
          "armFreeappDebugUnitTest",
          "armFreeappDebug",
          "armRetailappDebugAndroidTest",
          "armRetailappDebugUnitTest",
          "armRetailappDebug",
          "x86FreeappDebugAndroidTest",
          "x86FreeappDebugUnitTest",
          "x86FreeappDebug",
          "x86RetailappDebugAndroidTest",
          "x86RetailappDebugUnitTest",
          "x86RetailappDebug")
      assert(genProtoTaskNamesDebug == genProtoTasks.ofBuildType("debug").map { it.name }.toSet())

      val genProtoTaskNamesRelease = setOf(
          "armFreeappRelease",
          "armFreeappReleaseUnitTest",
          "armRetailappRelease",
          "armRetailappReleaseUnitTest",
          "x86FreeappRelease",
          "x86FreeappReleaseUnitTest",
          "x86RetailappRelease",
          "x86RetailappReleaseUnitTest")
      assert(genProtoTaskNamesRelease == genProtoTasks.ofBuildType("release").map { it.name }.toSet())

      assert(setOf("x86FreeappDebugAndroidTest") ==
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
