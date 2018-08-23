package com.google.protobuf.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke

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
