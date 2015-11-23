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

import com.google.common.collect.Maps
import org.gradle.api.*
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransform
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.internal.DefaultCppSourceSet
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.model.*
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder
import org.gradle.platform.base.TransformationFileType
import org.gradle.util.CollectionUtils

import javax.inject.Inject
import java.util.concurrent.Callable

class ProtobufModelPlugin implements Plugin<Project> {
  private final FileResolver fileResolver

  @Inject
  public ProtobufModelPlugin(FileResolver fileResolver) {
    this.fileResolver = fileResolver;
  }

  @Override
  void apply(Project project) {
    def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
    if (!gv || !gv.matches() || gv.group(1).toInteger() < 2 || gv.group(2).toInteger() < 7) {
      println("You are using Gradle ${project.gradle.gradleVersion}: This version of the protobuf" +
          " plugin for c++ requires minimum Gradle version 2.7")
    }

    // Provides the osdetector extension
    project.pluginManager.apply('osdetector')

    // TODO: The ProtobufConfigurator requires Project instance which can only be reference here.
    // ProtobufConfigurator would need to be refactor in order to avoid creating it as an extension
    // so we later reference it in the model. The end goal would be to create the ProtobufConfigurator
    // instance directly in the @Model rule.
    project.extensions.create("protobuf", ProtobufConfigurator, project, fileResolver);

    project.pluginManager.apply(ComponentModelBasePlugin)
  }

  static class Rules extends RuleSource {
    @Model
    ProtobufConfigurator protobuf(ExtensionContainer extensions) {
      return extensions.getByType(ProtobufConfigurator)
    }

    @Finalize
    void finishConfiguringTheGenerateProtoTask(TaskContainer tasks) {
      tasks.withType(GenerateProtoTask)*.doneConfig()
    }

    @Finalize
    void resolveProtobufTools(ProtobufConfigurator protobuf) {
      protobuf.tools.resolve()
    }

    @LanguageType
    void registerLanguage(LanguageTypeBuilder<ProtobufSourceSet> builder) {
      builder.setLanguageName("proto")
      builder.defaultImplementation(ProtobufSourceSet.class)
    }

    @Mutate
    void registerLanguageTransformation(LanguageTransformContainer languages, ProtobufConfigurator protobuf, ServiceRegistry serviceRegistry) {
      languages.add(new Proto(protobuf, serviceRegistry))
    }

    @Mutate
    void createDefaultProtobufSourceSets(ModelMap<NativeComponentSpec> components) {
      components.all { component ->
        if (!component.sources.containsKey('proto')) {
          component.sources.create("proto", ProtobufSourceSet)
        }
      }
    }

    @Finalize
    void bindDefaultProtoSourceSetToDefaultCppSourceSet(ModelMap<NativeComponentSpec> components) {
      components.all {
        sources.proto.generatedSourceSet.withType(CppSourceSet) {
          sources.cpp.lib it
        }
      }
    }

    @Mutate
    void configureGeneratedSourceSets(TaskContainer tasks, ModelMap<NativeBinarySpec> binaries,
                                      @Path("buildDir") File buildDir,
                                      ServiceRegistry serviceRegistry,
                                      ProtobufConfigurator protobuf) {
      final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);

      binaries.each { binary ->
        binary.tasks.withType(GenerateProtoTask) { task ->
          tasks.create("compile${task.name.capitalize()}Cpp", CppCompile) {
            SourceDirectorySet source = new DefaultSourceDirectorySet("${task.name}Cpp", "${task.name.capitalize()}Cpp", fileResolver);
            source.srcDir(new File("${task.outputBaseDir}/cpp"));
            it.source(source);
            it.includes(new File("${task.outputBaseDir}/cpp"));

            it.setDescription(String.format("Compiles the %s of %s", source, binary));
            it.setObjectFileDir(new File("${buildDir}/objs/${binary.namingScheme.outputDirectoryBase}/${task.name}Cpp"));

            def cppCompiler = binary.cppCompiler;
            it.setMacros(cppCompiler.getMacros());
            it.setCompilerArgs(cppCompiler.getArgs());
            it.setTargetPlatform(binary.getTargetPlatform());
            it.setToolChain(binary.getToolChain());
            it.includes(new Callable<List<FileCollection>>() {
              public List<FileCollection> call() {
                Collection<NativeDependencySet> libs = binary.getLibs();
                return CollectionUtils.collect(libs, new Transformer<FileCollection, NativeDependencySet>() {
                  public FileCollection transform(NativeDependencySet original) {
                    return original.getIncludeRoots();
                  }
                });
              }
            });


            it.dependsOn(task);

            binary.tasks.add(it);
            binary.builtBy(it);
            binary.binaryInputs(it.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
          }
        }
      }
    }
  }

  // TODO: This should probably be pushed into Gradle core.
  public class CppFile implements TransformationFileType {}

  private static class Proto implements LanguageTransform<ProtobufSourceSet, CppFile> {
    private final ProtobufConfigurator protobuf
    private final ServiceRegistry serviceRegistry

    public Proto(ProtobufConfigurator protobuf, ServiceRegistry serviceRegistry) {
      this.protobuf = protobuf
      this.serviceRegistry = serviceRegistry
    }

    public Class<ProtobufSourceSet> getSourceSetType() {
      return ProtobufSourceSet.class;
    }

    @Override
    Class<CppFile> getOutputType() {
      return CppFile.class
    }

    public Map<String, Class<?>> getBinaryTools() {
      Map<String, Class<?>> tools = Maps.newLinkedHashMap();
      return tools;
    }

    public SourceTransformTaskConfig getTransformTask() {
      new SourceTransformTaskConfig() {
        @Override
        String getTaskPrefix() {
          return "generate"
        }

        @Override
        Class<? extends DefaultTask> getTaskType() {
          return GenerateProtoTask
        }

        @Override
        void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
          final FileResolver fileResolver = serviceRegistry.get(FileResolver.class)
          final Instantiator instantiator = serviceRegistry.get(Instantiator.class)

          GenerateProtoTask compile = (GenerateProtoTask) task;
          compile.description = "Compiles Proto source for '${sourceSet.name}'"
          compile.outputBaseDir = new File("${protobuf.generatedFilesBaseDir}/${binary.name}/${sourceSet.name}")

          // Include sources
          compile.inputs.source sourceSet.source
          sourceSet.source.srcDirs.each { srcDir ->
            compile.include srcDir
          }

          compile.doneInitializing()
          compile.builtins {
            cpp {}
          }

          def cppSourceSet = BaseLanguageSourceSet.create(DefaultCppSourceSet.class, "${sourceSet.name}Cpp", sourceSet.name, fileResolver, instantiator);
          cppSourceSet.source.srcDir("${compile.outputBaseDir}/cpp")
          cppSourceSet.exportedHeaders.srcDir("${compile.outputBaseDir}/cpp")
          sourceSet.generatedSourceSet.add(cppSourceSet)
        }
      }
    }

    @Override
    boolean applyToBinary(BinarySpec binary) {
      return binary instanceof NativeBinarySpec
    }
  }
}
