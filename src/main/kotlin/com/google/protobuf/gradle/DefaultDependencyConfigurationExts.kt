package com.google.protobuf.gradle

import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create


/**
 * The 'protobuf' configuration.
 */
val ConfigurationContainer.protobuf: Configuration
    get() = getByName("protobuf")

fun DependencyHandler.protobuf(dependencyNotation: Any): Dependency? =
    add("protobuf", dependencyNotation)

inline fun DependencyHandler.protobuf(
    dependencyNotation: String,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit
): ExternalModuleDependency =
    add("protobuf", dependencyNotation, dependencyConfiguration)

fun DependencyHandler.protobuf(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null
): ExternalModuleDependency =
    create(group, name, version, configuration, classifier, ext).apply { add("protobuf", this) }

inline fun DependencyHandler.protobuf(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit
): ExternalModuleDependency =
    add("protobuf", create(group, name, version, configuration, classifier, ext), dependencyConfiguration)

inline fun <T : ModuleDependency> DependencyHandler.protobuf(dependency: T, dependencyConfiguration: T.() -> Unit): T =
    add("protobuf", dependency, dependencyConfiguration)

/**
 * The 'testProtobuf' configuration.
 */
val ConfigurationContainer.testProtobuf: Configuration
    get() = getByName("testProtobuf")

fun DependencyHandler.testProtobuf(dependencyNotation: Any): Dependency? =
    add("testProtobuf", dependencyNotation)

inline fun DependencyHandler.testProtobuf(
    dependencyNotation: String,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit
): ExternalModuleDependency =
    add("testProtobuf", dependencyNotation, dependencyConfiguration)

fun DependencyHandler.testProtobuf(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null
): ExternalModuleDependency =
    create(group, name, version, configuration, classifier, ext).apply { add("testProtobuf", this) }

inline fun DependencyHandler.testProtobuf(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit
): ExternalModuleDependency =
    add("testProtobuf", create(group, name, version, configuration, classifier, ext), dependencyConfiguration)

inline fun <T : ModuleDependency> DependencyHandler.testProtobuf(
    dependency: T,
    dependencyConfiguration: T.() -> Unit
): T =
    add("testProtobuf", dependency, dependencyConfiguration)
