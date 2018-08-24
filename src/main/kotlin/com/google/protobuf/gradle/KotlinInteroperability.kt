package com.google.protobuf.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.*


fun Project.protobuf(configuration: ProtobufConfigurator.() -> Unit) {
    project.convention.getPlugin(ProtobufConvention::class.java).protobuf.apply(configuration)
}

fun ProtobufConfigurator.protoc(closure: ExecutableLocator.() -> Unit) {
    protoc(delegateClosureOf(closure))
}

fun ProtobufConfigurator.plugins(closure: NamedDomainObjectContainerScope<ExecutableLocator>.() -> Unit) {
    plugins(delegateClosureOf<NamedDomainObjectContainer<ExecutableLocator>> {
        this{ this.closure() }
    })
}

fun <T : Any> NamedDomainObjectContainerScope<T>.id(id: String, closure: (T.() -> Unit)? = null) {
    closure?.let { create(id, delegateClosureOf(it)) }
            ?: create(id)
}

fun <T : Any> NamedDomainObjectContainerScope<T>.remove(id: String) {
    remove(this[id])
}

fun ProtobufConfigurator.generateProtoTasks(closure: ProtobufConfigurator.GenerateProtoTaskCollection.() -> Unit) {
    generateProtoTasks(delegateClosureOf(closure))
}

fun GenerateProtoTask.builtins(closure: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.() -> Unit) {
    builtins(delegateClosureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this{ this.closure() }
    })
}

fun GenerateProtoTask.plugins(closure: NamedDomainObjectContainerScope<GenerateProtoTask.PluginOptions>.() -> Unit) {
    plugins(delegateClosureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this{ this.closure() }
    })
}

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

/**
 * The 'protobuf' configuration.
 */
val ConfigurationContainer.protobuf: Configuration
    get() = getByName("protobuf")

fun DependencyHandler.protobuf(dependencyNotation: Any): Dependency? =
        add("protobuf", dependencyNotation)

inline fun DependencyHandler.protobuf(
        dependencyNotation: String,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
        add("protobuf", dependencyNotation, dependencyConfiguration)

fun DependencyHandler.protobuf(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null): ExternalModuleDependency =
        create(group, name, version, configuration, classifier, ext).apply { add("protobuf", this) }

inline fun DependencyHandler.protobuf(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
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
        dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
        add("testProtobuf", dependencyNotation, dependencyConfiguration)

fun DependencyHandler.testProtobuf(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null): ExternalModuleDependency =
        create(group, name, version, configuration, classifier, ext).apply { add("testProtobuf", this) }

inline fun DependencyHandler.testProtobuf(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
        add("testProtobuf", create(group, name, version, configuration, classifier, ext), dependencyConfiguration)

inline fun <T : ModuleDependency> DependencyHandler.testProtobuf(dependency: T, dependencyConfiguration: T.() -> Unit): T =
        add("testProtobuf", dependency, dependencyConfiguration)