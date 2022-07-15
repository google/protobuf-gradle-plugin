package com.google.protobuf.gradle

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.api.AndroidSourceSet as DeprecatedAndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.get

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
 * a receiver of type [DeprecatedAndroidSourceSet] for Android builds.
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
 * @receiver [DeprecatedAndroidSourceSet] The Android source set for which the "proto"
 * [SourceDirectorySet] extension will be configured
 *
 * @param action A configuration lambda to apply on a receiver of type [SourceDirectorySet]
 * @return [Unit]
 */
fun DeprecatedAndroidSourceSet.proto(action: SourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByName("proto")
        ?.let { it as?  SourceDirectorySet }
        ?.apply(action)
}

/**
 * Applies the supplied action to the "proto" [SourceDirectorySet] extension on
 * a receiver of type [AndroidSourceSet] for Android builds.
 *
 * @since 0.9.0
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
