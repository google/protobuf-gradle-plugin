/*
 * Original work copyright (c) 2015, Alex Antonov. All rights reserved.
 * Modified work copyright (c) 2015, Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.gradle.api.tasks.SourceSet
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

/**
 * The task that compiles proto files into Java files.
 */
public class GenerateProtoTask extends DefaultTask {

  private final List includeDirs = new ArrayList()
  private final NamedDomainObjectContainer<PluginOptions> builtins
  private final NamedDomainObjectContainer<PluginOptions> plugins

  // These fields are set by the Protobuf plugin only when initializing the
  // task.  Ideally they should be final fields, but Gradle task cannot have
  // constructor arguments. We use the initializing flag to prevent users from
  // accidentally modifying them.
  private String outputBaseDir
  // Tags for selectors inside protobuf.generateProtoTasks
  private SourceSet sourceSet
  private Object variant
  private ImmutableList<String> flavors
  private String buildType

  private boolean initializing = true
  private void checkInitializing() {
    Preconditions.checkState(initializing, 'Should not be called after initilization has finished')
  }

  void setOutputBaseDir(String outputBaseDir) {
    checkInitializing()
    Preconditions.checkState(this.outputBaseDir == null, 'outputBaseDir is already set')
    this.outputBaseDir = outputBaseDir
    outputs.dir outputBaseDir
  }

  void setSourceSet(SourceSet sourceSet) {
    checkInitializing()
    Preconditions.checkState(!Utils.isAndroidProject(project),
        'sourceSet should not be set in an Android project')
    this.sourceSet = sourceSet
  }

  void setVariant(Object variant) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'variant should not be set in a Java project')
    this.variant = variant
  }

  void setFlavors(ImmutableList<String> flavors) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'flavors should not be set in a Java project')
    this.flavors = flavors
  }

  void setBuildType(String buildType) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'buildType should not be set in a Java project')
    this.buildType = buildType
  }

  SourceSet getSourceSet() {
    Preconditions.checkState(!Utils.isAndroidProject(project),
        'sourceSet should not be used in an Android project')
    Preconditions.checkNotNull(sourceSet, 'sourceSet is not set')
    return sourceSet
  }

  Object getVariant() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'variant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return variant
  }

  ImmutableList<String> getFlavors() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'flavors should not be used in a Java project')
    Preconditions.checkNotNull(flavors, 'flavors is not set')
    return flavors
  }

  String getBuildType() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'buildType should not be used in a Java project')
    Preconditions.checkNotNull(buildType, 'buildType is not set')
    return buildType
  }

  void doneInitializing() {
    initializing = false
  }

  public GenerateProtoTask() {
    builtins = project.container(PluginOptions)
    plugins = project.container(PluginOptions)
  }

  //===========================================================================
  //        Configuration methods
  //===========================================================================

  /**
   * Configures the protoc builtins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void builtins(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, builtins)
  }

  /**
   * Configures the protoc plugins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void plugins(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, plugins)
  }

  /**
   * Add a directory to protoc's include path.
   */
  public void include(Object dir) {
      if (dir instanceof File) {
          includeDirs.add(dir)
      } else {
          includeDirs.add(project.file(dir))
      }
  }

  /**
   * The container of command-line options for a protoc plugin or a built-in output.
   */
  public static class PluginOptions implements Named {
    private final ArrayList<String> options = new ArrayList<String>()
    private final String name

    public PluginOptions(String name) {
      this.name = name
    }

    /**
     * Adds a plugin option.
     */
    public PluginOptions option(String option) {
      options.add(option)
      return this
    }

    public List<String> getOptions() {
      return options
    }

    /**
     * Returns the name of the plugin or builtin.
     */
    @Override
    public String getName() {
      return name
    }
  }

  //===========================================================================
  //    protoc invocation logic
  //===========================================================================

  // protoc allows you to prefix comma-delimited options to the path in
  // the --*_out flags, e.g.,
  // - Without options: --java_out=/path/to/output
  // - With options: --java_out=option1,option2:/path/to/output
  // This method generates the prefix out of the given options.
  def String makeOptionsPrefix(List<String> options) {
    StringBuilder prefix = new StringBuilder()
    if (!options.isEmpty()) {
      options.each { option ->
        if (prefix.length() > 0) {
          prefix.append(',')
        }
        prefix.append(option)
      }
      prefix.append(':')
    }
    return prefix.toString()
  }

  String getOutputDir(PluginOptions plugin) {
    return "${outputBaseDir}/${plugin.name}"
  }

  Collection<File> getAllOutputDirs() {
    ImmutableList.Builder<File> dirs = ImmutableList.builder()
    builtins.each { builtin ->
      dirs.add(new File(getOutputDir(builtin)))
    }
    plugins.each { plugin ->
      dirs.add(new File(getOutputDir(plugin)))
    }
    return dirs.build()
  }

  @TaskAction
  def compile() {
    Preconditions.checkState(!initializing, 'doneInitializing() has not been called')

    // Only at this point all outputs are finalized, then we can register the
    // generated source dirs to java compilation.

    // NOTE(zhangkun83): does this mean we can actually allow per-plugin output
    // dir reconfiguraiton? Not actually -- the basedir of all outputs must be
    // registered to task.outputs before Gradle starts to execute tasks, so
    // that the task can be correctly skipped.  We may only allow plugins to
    // output to a customized sub dir, but not to a random place, esp. out of
    // the pre-designated basedir. I doubt the usefulness of this
    // configurability given such restriction.
    if (Utils.isAndroidProject(project)) {
      variant.registerJavaGeneratingTask(this, getAllOutputDirs())
    } else {
      // Add generated java sources to java source sets that will be compiled.
      getAllOutputDirs().each { dir ->
        sourceSet.java.srcDir(dir)
      }
    }

    ToolsLocator tools = project.protobuf.tools
    Set<File> protoFiles = inputs.sourceFiles.files

    [builtins, plugins]*.each { plugin ->
      File outputDir = new File(getOutputDir(plugin))
      outputDir.mkdirs()
    }

    def dirs = includeDirs*.path.collect {"-I${it}"}
    logger.debug "ProtobufCompile using directories ${dirs}"
    logger.debug "ProtobufCompile using files ${protoFiles}"
    def cmd = [ tools.protoc.path ]
    cmd.addAll(dirs)

    // Handle code generation built-ins
    builtins.each { builtin ->
      String outPrefix = makeOptionsPrefix(builtin.options)
      cmd += "--${builtin.name}_out=${outPrefix}${getOutputDir(builtin)}"
    }

    // Handle code generation plugins
    plugins.each { plugin ->
      String name = plugin.name
      ExecutableLocator locator = tools.plugins.findByName(name)
      if (locator == null) {
        throw new GradleException("Codegen plugin ${name} not defined")
      }
      String pluginOutPrefix = makeOptionsPrefix(plugin.options)
      cmd += "--${name}_out=${pluginOutPrefix}${getOutputDir(plugin)}"
      cmd += "--plugin=protoc-gen-${name}=${locator.path}"
    }

    cmd.addAll protoFiles
    logger.log(LogLevel.INFO, cmd.toString())
    def stdout = new StringBuffer()
    def stderr = new StringBuffer()
    Process result = cmd.execute()
    result.waitForProcessOutput(stdout, stderr)
    def output = "protoc: stdout: ${stdout}. stderr: ${stderr}"
    if (result.exitValue() == 0) {
      logger.log(LogLevel.INFO, output)
    } else {
      throw new GradleException(output)
    }
  }

}
