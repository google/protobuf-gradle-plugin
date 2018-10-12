package com.google.protobuf.gradle

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create

interface ProtobufDependencyHelperProvider {

    operator fun get(configurationName: String): ProtobufDependencyHelper

    operator fun get(configuration: Configuration): ProtobufDependencyHelper =
        get(configuration.name)
}

inline val DependencyHandler.protobuf: ProtobufDependencyHelperProvider
    get() = object : ProtobufDependencyHelperProvider {
        override fun get(configurationName: String) =
            ProtobufDependencyHelper(configurationName, this@protobuf)
    }


class ProtobufDependencyHelper(
    configuration: String,
    private val dependencyHandler: DependencyHandler
) {

    private val configurationName: String = "${configuration}Protobuf"

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

}