package com.google.protobuf.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.get

/**
 * Applies the supplied action to the project's instance of [ProtobufConfigurator].
 *
 * @since 0.8.7
 * @usage
 * ```
 * protobuf {
 *     ...
 *     generatedFilesBaseDir = files(...)
 * }
 * ```
 *
 * @receiver [Project] The project for which the plugin configuration will be applied
 * @param action A configuration lambda to apply on a receiver of type [ProtobufConfigurator]
 *
 * @return [Unit]
 */
fun Project.protobuf(action: ProtobufConfigurator.()->Unit) {
    project.convention.getPlugin(ProtobufConvention::class.java).protobuf.apply(action)
}

/**
 * Applies the supplied action to the [ProtobufSourceDirectorySet] extension on
 * a receiver of type [SourceSet]
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
 * @receiver [SourceSet] The source set for which the [ProtobufSourceDirectorySet] extension
 * will be configured
 *
 * @param action A configuration lambda to apply on a receiver of type [ProtobufSourceDirectorySet]
 * @return [Unit]
 */
fun SourceSet.proto(action: ProtobufSourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByType(ProtobufSourceDirectorySet::class.java)
        ?.apply(action)
}

/**
 * Uses the supplied action to configure the [ExecutableLocator] for protoc.
 *
 * @since 0.8.7
 * @usage
 * ```
 * protobuf {
 *     protoc {
 *         artifact = "com.google.protobuf:protoc:3.6.1"
 *     }
 * }
 * ```
 *
 * @receiver [ProtobufConfigurator] The protobuf plugin configuration instance
 * for the project.
 *
 * @param action A lambda with receiver of type [ExecutableLocator] used
 * for configuring the locator for protoc
 *
 * @return [Unit]
 */
fun ProtobufConfigurator.protoc(action: ExecutableLocator.() -> Unit) {
    protoc(closureOf(action))
}

fun ProtobufConfigurator.plugins(action: NamedDomainObjectContainerScope<ExecutableLocator>.() -> Unit) {
    plugins(closureOf<NamedDomainObjectContainer<ExecutableLocator>> {
        KtDslCompatibilityUtils.configureNamedDomainObjectContainer<ExecutableLocator>(this, action)
    })
}

fun ProtobufConfigurator.generateProtoTasks(action: ProtobufConfigurator.GenerateProtoTaskCollection.()->Unit) {
    generateProtoTasks(closureOf(action))
}

fun GenerateProtoTask.builtins(action: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.()->Unit) {
    builtins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        KtDslCompatibilityUtils.configureNamedDomainObjectContainer<GenerateProtoTask.PluginOptions>(this, action)
    })
}

fun GenerateProtoTask.plugins(action: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.()-> Unit) {
    plugins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        KtDslCompatibilityUtils.configureNamedDomainObjectContainer<GenerateProtoTask.PluginOptions>(this, action)
    })
}

/**
 * An extension for creating and configuring the elements of an instance of [NamedDomainObjectContainer].
 *
 * @since 0.8.7
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
 * @receiver [NamedDomainObjectContainerScope] The scope of the [NamedDomainObjectContainer]
 * on which to create or configure an element.
 *
 * @param id The string id of the element to create or configure.
 * @param action An optional action that will be applied to the element instance.
 *
 * @return [Unit]
 */
fun <T : Any> NamedDomainObjectContainerScope<T>.id(id: String, action: (T.() -> Unit)? = null) {
    action?.let { create(id, closureOf(it)) }
            ?: create(id)
}


/**
 * An extension for removing an element by id on an instance of [NamedDomainObjectContainer].
 *
 * @since 0.8.7
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
 * @receiver [NamedDomainObjectContainerScope] The scope of the [NamedDomainObjectContainer]
 * on which to remove an element.
 *
 * @param id The string id of the element to remove.
 *
 * @return [Unit]
 */
fun <T : Any> NamedDomainObjectContainerScope<T>.remove(id: String) {
    remove(this[id])
}

/**
 * The method generatorProtoTasks applies the supplied closure to the
 * instance of [ProtobufConfigurator.GenerateProtoTaskCollection].
 *
 * Since [ProtobufConfigurator.JavaGenerateProtoTaskCollection] and [ProtobufConfigurator.AndroidGenerateProtoTaskCollection]
 * each have unique methods, and only one instance is allocated per project, we need a way to statically resolve the
 * available methods. This is a necessity since Kotlin does not have any dynamic method resolution capabilities.
 */

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofSourceSet(sourceSet: String): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.JavaGenerateProtoTaskCollection)
        this.ofSourceSet(sourceSet) else emptyList()

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofFlavor(flavor: String): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.AndroidGenerateProtoTaskCollection)
        this.ofFlavor(flavor) else emptyList()

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofBuildType(buildType: String): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.AndroidGenerateProtoTaskCollection)
        this.ofBuildType(buildType) else emptyList()

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofVariant(variant: String): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.AndroidGenerateProtoTaskCollection)
        this.ofVariant(variant) else emptyList()

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofNonTest(): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.AndroidGenerateProtoTaskCollection)
        this.ofNonTest() else emptyList()

fun ProtobufConfigurator.GenerateProtoTaskCollection.ofTest(): Collection<GenerateProtoTask> =
    if (this is ProtobufConfigurator.AndroidGenerateProtoTaskCollection)
        this.ofTest() else emptyList()
