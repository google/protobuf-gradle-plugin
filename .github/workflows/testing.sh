#!/bin/bash

set -eu -o pipefail

export GRADLE_OPTS="-Xms128m"

./gradlew clean assemble -x signArchives test --tests com.google.protobuf.gradle.ProtobufJavaPluginTest --stacktrace
./gradlew test --tests com.google.protobuf.gradle.ProtobufKotlinDslCopySpecTest --stacktrace
./gradlew test --tests com.google.protobuf.gradle.ProtobufKotlinDslPluginTest --stacktrace
./gradlew test --tests com.google.protobuf.gradle.ProtobufAndroidPluginTest --stacktrace
./gradlew -stop
./gradlew test --tests com.google.protobuf.gradle.ProtobufAndroidPluginKotlinTest --stacktrace
./gradlew test --tests com.google.protobuf.gradle.AndroidProjectDetectionTest --stacktrace
./gradlew codenarcMain || (cat ./build/reports/codenarc/main.txt && false)
./gradlew codenarcTest || (cat ./build/reports/codenarc/test.txt && false)
