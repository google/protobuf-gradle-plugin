/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.protobuf.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.cpp.CppSourceSet
import org.gradle.model.*
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder

import javax.inject.Inject

class ProtobufModelPlugin implements Plugin<Project> {
    private final FileResolver fileResolver

    @Inject
    public ProtobufModelPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    void apply(Project project) {
        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1).toInteger() < 2 || gv.group(2).toInteger() < 5) {
            println("You are using Gradle ${project.gradle.gradleVersion}: This version of the protobuf plugin for c++ requires minimum Gradle version 2.5")
        }

        // Provides the osdetector extension
        project.pluginManager.apply('osdetector')

        //project.convention.plugins.protobuf = new ProtobufConvention(project, fileResolver);
        project.extensions.create("protobuf", ProtobufConfigurator, project, fileResolver);

        project.pluginManager.apply(ComponentModelBasePlugin)
    }

    static class Rules extends RuleSource {
        @Model
        ProtobufConfigurator protobuf(ExtensionContainer extensions) {
            return extensions.getByType(ProtobufConfigurator)
        }

        @LanguageType
        void registerLanguage(LanguageTypeBuilder<ProtobufSourceSet> builder) {
            builder.setLanguageName("proto")
            builder.defaultImplementation(ProtobufSourceSet.class)
        }

        @Mutate
        void createDefaultProtoSourceSets(ModelMap<NativeComponentSpec> components) {
            components.all { component ->
                component.sources.create("proto", ProtobufSourceSet)
            }
        }

        @Mutate
        void createCppAssociatedSourceSets(ModelMap<NativeComponentSpec> components) {
            components.all { component ->
                component.sources.withType(ProtobufSourceSet) { language ->
                    component.sources.create("cpp${language.name.capitalize()}", CppSourceSet)
                }
            }
        }

        @Mutate
        void bindDefaultCppSourceSetWithDefaultProtoSourceSet(ModelMap<NativeComponentSpec> components) {
            components.all { component ->
                component.sources.withType(CppSourceSet) { language ->
                    if (language.name == "cppProto") {
                        component.sources.cpp.lib language
                    }
                }
            }
        }

        @Mutate
        void createGenerateProtoTasks(TaskContainer tasks, ModelMap<NativeComponentSpec> components, @Path("buildDir") File buildDir, ProtobufConfigurator protobuf) {
            components.values().each { component ->
                component.sources.withType(ProtobufSourceSet) { language ->
                    component.sources."cpp${language.name.capitalize()}" {
                        def generateProtoTaskName = "generate${component.name.capitalize()}${language.name.capitalize()}"
                        def outputDir = new File("${protobuf.generatedFilesBaseDir}/${language.name}")

                        def generateProtoTask = tasks.create(generateProtoTaskName, GenerateProtoTask) {
                            description = "Compiles Proto source for '${language.name}'"
                            outputBaseDir = outputDir.path

                            // Include sources
                            inputs.source language.source
                            language.source.srcDirs.each { srcDir ->
                                include srcDir
                            }

//                            // Include extracted sources
//                            ConfigurableFileTree extractedProtoSources =
//                                    project.fileTree(getExtractedProtosDir(sourceSet.name)) {
//                                        include "**/*.proto"
//                                    }
//                            inputs.source extractedProtoSources
//                            include extractedProtoSources.dir

//                            // Register extracted include protos
//                            ConfigurableFileTree extractedIncludeProtoSources =
//                                    project.fileTree(getExtractedIncludeProtosDir(sourceSet.name)) {
//                                        include "**/*.proto"
//                                    }
//                            // Register them as input, but not as "source".
//                            // Inputs are checked in incremental builds, but only "source" files are compiled.
//                            inputs.dir extractedIncludeProtoSources
//                            // Add the extracted include dir to the --proto_path include paths.
//                            include extractedIncludeProtoSources.dir

                            //sourceSet = language.source
                            doneInitializing()
                            builtins {
                                cpp {}
                            }
                        }
                    //t.source language.source
                    //t.sourceDir = new File("${buildDir}/src/${component.name}/cpp${language.name.capitalize()}")
                        //t.includeDirs.addAll(language.source.srcDirs)
                        builtBy generateProtoTask
                        source {
                            srcDir new File("${outputDir.path}/cpp")
                        }
                        exportedHeaders {
                            srcDir new File("${outputDir.path}/cpp")
                        }
                    }
                }
            }
        }

        @Finalize
        void finalizeGenerateProtoTasks(TaskContainer tasks) {
            tasks.withType(GenerateProtoTask)*.doneConfig()
        }

        @Finalize
        void finalizeProtobufConvention(ProtobufConfigurator protobuf) {
            protobuf.tools.resolve()
        }

    }
}
