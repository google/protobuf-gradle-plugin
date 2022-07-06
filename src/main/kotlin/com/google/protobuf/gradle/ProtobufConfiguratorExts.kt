package com.google.protobuf.gradle

import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke

/**
 * Applies the supplied action to the project's instance of [ProtobufExtension].
 *
 * @since 0.9.0
 * @usage
 * ```
 * protobuf {
 *     ...
 *     generatedFilesBaseDir = files(...)
 * }
 * ```
 *
 * @receiver [Project] The project for which the plugin configuration will be applied
 * @param action A configuration lambda to apply on a receiver of type [ProtobufExtension]
 *
 * @return [Unit]
 */
fun Project.protobuf(action: ProtobufExtension.() -> Unit) {
    project.extensions.getByType(ProtobufExtension::class.java).apply(action)
}

/**
 * Applies the supplied action to the "proto" [SourceDirectorySet] extension on
 * a receiver of type [SourceSet].
 *
 * @since 0.8.7
 * @usage
 * ```
 * sourceSets {
 *     create("sample") {
 *         proto {
 *             srcDir("src/sample/protobuf")
 *         }
 *     }
 * }
 * ```
 *
 * @receiver [SourceSet] The source set for which the "proto" [SourceDirectorySet] extension
 * will be configured
 *
 * @param action A configuration lambda to apply on a receiver of type [SourceDirectorySet]
 * @return [Unit]
 */
fun SourceSet.proto(action: SourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByName("proto")
        ?.let { it as? SourceDirectorySet }
        ?.apply(action)
}

/**
 * Applies the supplied action to the "proto" [SourceDirectorySet] extension on
 * a receiver of type [AndroidSourceSet] for Android builds.
 *
 * @since 0.8.15
 * @usage
 * ```
 * android {
 *     sourceSets {
 *         create("sample") {
 *             proto {
 *                 srcDir("src/sample/protobuf")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @receiver [AndroidSourceSet] The Android source set for which the "proto"
 * [SourceDirectorySet] extension will be configured
 *
 * @param action A configuration lambda to apply on a receiver of type [SourceDirectorySet]
 * @return [Unit]
 */
fun AndroidSourceSet.proto(action: SourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByName("proto")
        ?.let { it as? SourceDirectorySet }
        ?.apply(action)
}

fun GenerateProtoTask.builtins(action: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.() -> Unit) {
    builtins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this.invoke(action)
    })
}

fun GenerateProtoTask.plugins(action: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.() -> Unit) {
    plugins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this.invoke(action)
    })
}

/**
 * An extension for creating and configuring the elements of an instance of [NamedDomainObjectContainer].
 *
 * @since 0.9.0
 * @usage
 * ```
 * protobuf {
 *     plugins {
 *         id("grpc") {
 *             artifact = "io.grpc:protoc-gen-grpc-java:1.15.1"
 *         }
 *     }
 * }
 * ```
 *
 * @receiver [NamedDomainObjectContainer] The scope of the [NamedDomainObjectContainer]
 * on which to create or configure an element.
 *
 * @param id The string id of the element to create or configure.
 * @param action An optional action that will be applied to the element instance.
 *
 * @return [Unit]
 */
fun <T : Any> NamedDomainObjectContainer<T>.id(id: String, action: (T.() -> Unit)? = null) {
    action?.let { create(id, it) } ?: create(id)
}

/**
 * An extension for removing an element by id on an instance of [NamedDomainObjectContainer].
 *
 * @since 0.9.0
 * @usage
 * ```
 * protobuf {
 *     generateProtoTasks {
 *         ofSourceSet("main").forEach {
 *             it.builtins {
 *                 remove("java")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @receiver [NamedDomainObjectContainer] The scope of the [NamedDomainObjectContainer]
 * on which to remove an element.
 *
 * @param id The string id of the element to remove.
 *
 * @return [Unit]
 */
fun <T : Any> NamedDomainObjectContainer<T>.remove(id: String) {
    remove(this[id])
}

/**
 * The method generatorProtoTasks applies the supplied closure to the
 * instance of [ProtobufExtension.GenerateProtoTaskCollection].
 *
 * Since [ProtobufExtension.JavaGenerateProtoTaskCollection] and [ProtobufExtension.AndroidGenerateProtoTaskCollection]
 * each have unique methods, and only one instance is allocated per project, we need a way to statically resolve the
 * available methods. This is a necessity since Kotlin does not have any dynamic method resolution capabilities.
 */

fun ProtobufExtension.GenerateProtoTaskCollection.ofSourceSet(sourceSet: String): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.JavaGenerateProtoTaskCollection)
        this.ofSourceSet(sourceSet) else emptyList()

fun ProtobufExtension.GenerateProtoTaskCollection.ofFlavor(flavor: String): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.AndroidGenerateProtoTaskCollection)
        this.ofFlavor(flavor) else emptyList()

fun ProtobufExtension.GenerateProtoTaskCollection.ofBuildType(buildType: String): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.AndroidGenerateProtoTaskCollection)
        this.ofBuildType(buildType) else emptyList()

fun ProtobufExtension.GenerateProtoTaskCollection.ofVariant(variant: String): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.AndroidGenerateProtoTaskCollection)
        this.ofVariant(variant) else emptyList()

fun ProtobufExtension.GenerateProtoTaskCollection.ofNonTest(): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.AndroidGenerateProtoTaskCollection)
        this.ofNonTest() else emptyList()

fun ProtobufExtension.GenerateProtoTaskCollection.ofTest(): Collection<GenerateProtoTask> =
    if (this is ProtobufExtension.AndroidGenerateProtoTaskCollection)
        this.ofTest() else emptyList()
