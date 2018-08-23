package com.google.protobuf.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.invoke

fun Project.protobuf(configuration: ProtobufConfigurator.() -> Unit) {
    project.convention.getPlugin(ProtobufConvention::class.java).protobuf.apply(configuration)
}

fun ProtobufConfigurator.protoc(closure: ExecutableLocator.()->Unit){
    protoc(delegateClosureOf(closure))
}

fun ProtobufConfigurator.plugins(closure: NamedDomainObjectContainer<ExecutableLocator>.()->Unit){
    plugins(delegateClosureOf<NamedDomainObjectContainer<ExecutableLocator>> {
        this(closure)
    })
}

fun ProtobufConfigurator.generateProtoTasks(closure: ProtobufConfigurator.GenerateProtoTaskCollection.()->Unit){
    delegateClosureOf(closure)
}

fun GenerateProtoTask.builtins(closure: NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>.()->Unit){
    builtins(delegateClosureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this(closure)
    })
}

fun GenerateProtoTask.plugins(closure: NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>.()->Unit){
    plugins(delegateClosureOf<NamedDomainObjectContainer<GenerateProtoTask.PluginOptions>> {
        this(closure)
    })
}
