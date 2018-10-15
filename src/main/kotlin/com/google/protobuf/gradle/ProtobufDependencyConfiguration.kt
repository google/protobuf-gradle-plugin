package com.google.protobuf.gradle

import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create


val ConfigurationContainer.protobuf: Configuration
    get() = getByName("protobuf")

inline val DependencyHandler.protobuf: ProtobufDependencyHelper.Default
    get() = ProtobufDependencyHelper.Default(dependencyHandler = this)

val ConfigurationContainer.testProtobuf: Configuration
    get() = getByName("testProtobuf")

inline val DependencyHandler.testProtobuf: ProtobufDependencyHelper
    get() = protobuf["test"]

interface ProtobufDependencyHelper {

    val dependencyHandler: DependencyHandler

    val configurationName: String

    operator fun invoke(dependencyNotation: Any): Dependency? =
        dependencyHandler.add(configurationName, dependencyNotation)

    operator fun invoke(
        dependencyNotation: String,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ): ExternalModuleDependency =
        dependencyHandler.add(configurationName, dependencyNotation, dependencyConfiguration)

    operator fun invoke(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null
    ): ExternalModuleDependency =
        dependencyHandler.run {
            create(group, name, version, configuration, classifier, ext)
                .also { add(configurationName, it) }
        }

    operator fun invoke(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ): ExternalModuleDependency =
        dependencyHandler.run {
            val dep = create(group, name, version, configuration, classifier, ext)
            add(configurationName, dep, dependencyConfiguration)
        }

    operator fun <T : ModuleDependency> invoke(
        dependency: T,
        dependencyConfiguration: T.() -> Unit
    ): T =
        dependencyHandler.add(configurationName, dependency, dependencyConfiguration)

    class Default(
        override val configurationName: String = "protobuf",
        override val dependencyHandler: DependencyHandler
    ) : ProtobufDependencyHelper, ProtobufDependencyHelperProvider {

        override fun get(sourceSetName: String): ProtobufDependencyHelper =
            Default(
                Utils.getConfigName(sourceSetName, "protobuf"),
                dependencyHandler
            )
    }
}

interface ProtobufDependencyHelperProvider {

    operator fun get(sourceSetName: String): ProtobufDependencyHelper

}