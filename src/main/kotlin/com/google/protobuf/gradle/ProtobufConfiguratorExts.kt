package com.google.protobuf.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*


fun Project.protobuf(action: ProtobufConfigurator.()->Unit) {
    project.convention.getPlugin(ProtobufConvention::class.java).protobuf.apply(action)
}

fun SourceSet.proto(action: ProtobufSourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByType(ProtobufSourceDirectorySet::class.java)
        ?.apply(action)
}

fun ProtobufConfigurator.protoc(closure: ExecutableLocator.() -> Unit) {
    protoc(closureOf(closure))
}

fun ProtobufConfigurator.plugins(closure: NamedDomainObjectContainerScope<ExecutableLocator>.() -> Unit) {
    plugins(closureOf<NamedDomainObjectContainer<ExecutableLocator>> {
        closure(NamedDomainObjectContainerScope.of(this))
    })
}

fun <T : Any> NamedDomainObjectContainerScope<T>.id(id: String, closure: (T.() -> Unit)? = null) {
    closure?.let { create(id, closureOf(it)) }
        ?: create(id)
}

fun <T : Any> NamedDomainObjectContainerScope<T>.remove(id: String) {
    remove(this[id])
}

fun ProtobufConfigurator.generateProtoTasks(closure: ProtobufConfigurator.GenerateProtoTaskCollection.()->Unit) {
    generateProtoTasks(closureOf(closure))
}

fun GenerateProtoTask.builtins(configuration: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.()->Unit) {
    builtins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        configuration(NamedDomainObjectContainerScope.of(this))
    })
}

fun GenerateProtoTask.plugins(configuration: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.()-> Unit) {
    plugins(closureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        configuration(NamedDomainObjectContainerScope.of(this))
    })
}

/**
 * The method generatorProtoTasks applies the supplied closure to the
 * instance of [ProtobufConfigurator.GenerateProtoTaskCollection].
 *
 * Since [ProtobufConfigurator.JavaGenerateProtoTaskCollection] and [ProtobufConfigurator.AndroidGenerateProtoTaskCollection]
 * each have unique methods, and only one instance in allocated per project, we need a way to statically resolve the
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
