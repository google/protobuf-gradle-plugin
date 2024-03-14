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

import com.google.protobuf.gradle.internal.DefaultProtoSourceSet
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection

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
  private final ArrayList<Action<GenerateProtoTaskCollection>> taskConfigActions
  private final NamedDomainObjectContainer<ProtoSourceSet> sourceSets

  @PackageScope
  final Provider<String> defaultGeneratedFilesBaseDir

  @PackageScope
  final Provider<String> defaultJavaExecutablePath

  public ProtobufExtension(final Project project) {
    this.project = project
    this.tasks = new GenerateProtoTaskCollection(project)
    this.tools = new ToolsLocator(project)
    this.taskConfigActions = []
    this.defaultGeneratedFilesBaseDir = project.layout.buildDirectory.dir("generated/source/proto").map {
      it.asFile.path
    }
    this.generatedFilesBaseDirProperty.convention(defaultGeneratedFilesBaseDir)
    this.defaultJavaExecutablePath = project.provider {
      computeJavaExePath()
    }
    this.javaExecutablePath.convention(defaultJavaExecutablePath)
    this.sourceSets = project.objects.domainObjectContainer(ProtoSourceSet) { String name ->
      new DefaultProtoSourceSet(name, project.objects)
    }
  }

  static String computeJavaExePath() throws IOException {
    File java = new File(System.getProperty("java.home"), Utils.isWindows() ? "bin/java.exe" : "bin/java")
    if (!java.exists()) {
      throw new IOException("Could not find java executable at " + java.path)
    }
    return java.path
  }

  @PackageScope
  NamedDomainObjectContainer<ProtoSourceSet> getSourceSets() {
    return this.sourceSets
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

  /**
   * The location of the java executable used to run java based
   * code generation plugins. The default is the java executable
   * running gradle.
   */
  abstract Property<String> getJavaExecutablePath()

  @PackageScope
  void configureTasks() {
    this.taskConfigActions.each { action ->
      action.execute(tasks)
    }
  }

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

  /**
   * Locate the codegen plugin executables. The closure will be manipulating a
   * NamedDomainObjectContainer<ExecutableLocator>.
   */
  public void plugins(Action<NamedDomainObjectContainer<ExecutableLocator>> configureAction) {
    configureAction.execute(tools.plugins)
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
  public void generateProtoTasks(Action<GenerateProtoTaskCollection> configureAction) {
    taskConfigActions.add(configureAction)
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

  public class GenerateProtoTaskCollection {
    private final Project project

    GenerateProtoTaskCollection(final Project project) {
      this.project = project
    }

    public TaskCollection<GenerateProtoTask> all() {
      return project.tasks.withType(GenerateProtoTask)
    }

    public TaskCollection<GenerateProtoTask> ofSourceSet(String sourceSet) {
      return all().matching { GenerateProtoTask task ->
        !Utils.isAndroidProject(project) && task.sourceSet.name == sourceSet
      }
    }

    public TaskCollection<GenerateProtoTask> ofFlavor(String flavor) {
      return all().matching { GenerateProtoTask task ->
        Utils.isAndroidProject(project) && task.flavors.contains(flavor)
      }
    }

    public TaskCollection<GenerateProtoTask> ofBuildType(String buildType) {
      return all().matching { GenerateProtoTask task ->
        Utils.isAndroidProject(project) && task.buildType == buildType
      }
    }

    @TypeChecked(TypeCheckingMode.SKIP) // Don't depend on AGP
    public TaskCollection<GenerateProtoTask> ofVariant(String variant) {
      return all().matching { GenerateProtoTask task ->
        Utils.isAndroidProject(project) && task.variant.name == variant
      }
    }

    public TaskCollection<GenerateProtoTask> ofNonTest() {
      return all().matching { GenerateProtoTask task ->
        Utils.isAndroidProject(project) && !task.isTestVariant
      }
    }

    public TaskCollection<GenerateProtoTask> ofTest() {
      return all().matching { GenerateProtoTask task ->
        Utils.isAndroidProject(project) && task.isTestVariant
      }
    }
  }
}
