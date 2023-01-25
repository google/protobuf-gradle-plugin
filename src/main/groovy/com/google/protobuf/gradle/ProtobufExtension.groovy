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

import com.google.protobuf.gradle.internal.ProtoSourceSetObjectFactory
import com.google.protobuf.gradle.internal.ProtoVariantObjectFactory
import com.google.protobuf.gradle.tasks.GenerateProtoTaskSpec
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import com.google.protobuf.gradle.tasks.ProtoVariant
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.util.ConfigureUtil

/**
 * Adds the protobuf {} block as a property of the project.
 */
@CompileStatic
// gradle require abstract modificator on extensions
@SuppressWarnings(["AbstractClassWithoutAbstractMethod", "AbstractClassWithPublicConstructor"])
abstract class ProtobufExtension {
  private final Project project
  private final GenerateProtoTaskCollection tasks
  private final ToolsLocator tools
  private final NamedDomainObjectContainer<ProtoSourceSet> sourceSets
  private final NamedDomainObjectContainer<ProtoVariant> variants

  @PackageScope
  final String defaultGeneratedFilesBaseDir

  public ProtobufExtension(final Project project) {
    this.project = project
    this.tasks = new GenerateProtoTaskCollection(project)
    this.tools = new ToolsLocator(project)

    this.defaultGeneratedFilesBaseDir = "${project.buildDir}/generated/source/proto"
    this.generatedFilesBaseDirProperty.convention(defaultGeneratedFilesBaseDir)

    ObjectFactory objects = project.objects
    this.sourceSets = project.objects.domainObjectContainer(ProtoSourceSet, new ProtoSourceSetObjectFactory(objects))
    this.variants = project.objects.domainObjectContainer(ProtoVariant, new ProtoVariantObjectFactory(project))
  }

  @PackageScope
  NamedDomainObjectContainer<ProtoSourceSet> getSourceSets() {
    return this.sourceSets
  }

  @PackageScope
  NamedDomainObjectContainer<ProtoVariant> getVariants() {
    return variants
  }

  @PackageScope
  ToolsLocator getTools() {
    return tools
  }

  String getGeneratedFilesBaseDir() {
    return generatedFilesBaseDirProperty.get()
  }

  @Deprecated
  void setGeneratedFilesBaseDir(String generatedFilesBaseDir) {
    generatedFilesBaseDirProperty.set(generatedFilesBaseDir)
  }

  /**
   * The base directory of generated files. The default is
   * "${project.buildDir}/generated/source/proto".
   */
  @PackageScope
  abstract Property<String> getGeneratedFilesBaseDirProperty()

  //===========================================================================
  //         Configuration methods
  //===========================================================================

  /**
   * Locates the protoc executable. The closure will be manipulating an
   * ExecutableLocator.
   */
  public void protoc(Action<ExecutableLocator> configureAction) {
    configureAction.execute(tools.protoc)
  }

  public void protoc(@DelegatesTo(ExecutableLocator) Closure<ExecutableLocator> closure) {
    ConfigureUtil.configure(closure, tools.protoc)
  }

  /**
   * Locate the codegen plugin executables. The closure will be manipulating a
   * NamedDomainObjectContainer<ExecutableLocator>.
   */
  public void plugins(Action<NamedDomainObjectContainer<ExecutableLocator>> configureAction) {
    configureAction.execute(tools.plugins)
  }

  public void plugins(Closure<NamedDomainObjectContainer<ExecutableLocator>> closure) {
    ConfigureUtil.configure(closure, tools.plugins)
  }

  /**
   * Configures the generateProto tasks in the given closure.
   *
   * <p>The closure will be manipulating a JavaGenerateProtoTaskCollection or
   * an AndroidGenerateProtoTaskCollection depending on whether the project is
   * Java or Android.
   *
   * <p>You should only change the generateProto tasks in this closure. Do not
   * change the task in your own afterEvaluate closure, as the change may not
   * be picked up correctly by the wired javaCompile task.
   */
  void generateProtoTasks(Action<GenerateProtoTaskCollection> action) {
    action.execute(tasks)
  }

  void generateProtoTasks(@DelegatesTo(GenerateProtoTaskCollection) Closure<GenerateProtoTaskCollection> closure) {
    ConfigureUtil.configure(closure, tasks)
  }

  /**
   * Returns the collection of generateProto tasks. Note the tasks are
   * available only after project evaluation.
   *
   * <p>Do not try to change the tasks other than in the closure provided
   * to {@link #generateProtoTasks(Closure)}. The reason is explained
   * in the comments for the linked method.
   */
  public GenerateProtoTaskCollection getGenerateProtoTasks() {
    return tasks
  }

  @CompileStatic
  public class GenerateProtoTaskCollection {
    private final Project project

    GenerateProtoTaskCollection(final Project project) {
      this.project = project
    }

    private NamedDomainObjectSet<ProtoVariant> variants() {
      return project.extensions.getByType(ProtobufExtension).variants
    }

    void all(Action<GenerateProtoTaskSpec> action) {
      variants.all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void all(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      all(ConfigureUtil.configureUsing(closure))
    }

    void ofSourceSet(String sourceSet, Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> !Utils.isAndroidProject(project) && variant.sourceSet == sourceSet }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofSourceSet(String sourceSet, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofSourceSet(sourceSet, ConfigureUtil.configureUsing(closure))
    }

    void ofFlavor(String flavor, Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.flavors.contains(flavor) }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofFlavor(String flavor, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofFlavor(flavor, ConfigureUtil.configureUsing(closure))
    }

    void ofBuildType(String buildType, Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.buildType == buildType }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofBuildType(String buildType, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofBuildType(buildType, ConfigureUtil.configureUsing(closure))
    }

    void ofVariant(String name, Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.name == name }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofVariant(String name, @DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofVariant(name, ConfigureUtil.configureUsing(closure))
    }

    void ofNonTest(Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && !variant.isTest }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofNonTest(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofNonTest(ConfigureUtil.configureUsing(closure))
    }

    void ofTest(Action<GenerateProtoTaskSpec> action) {
      variants()
        .matching { ProtoVariant variant -> Utils.isAndroidProject(project) && variant.isTest }
        .all { ProtoVariant variant -> action.execute(variant.generateProtoTaskSpec) }
    }

    void ofTest(@DelegatesTo(GenerateProtoTaskSpec) Closure<GenerateProtoTaskSpec> closure) {
      ofTest(ConfigureUtil.configureUsing(closure))
    }
  }
}
