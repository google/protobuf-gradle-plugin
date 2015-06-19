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

import org.gradle.api.tasks.Input
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

public class GenerateProtoTask extends DefaultTask {

  @Input
  private final List includeDirs = new List()

  private final ToolsLocator toolsLocator

  private final NamedDomainObjectContainer<PluginOptions> builtins
  private final NamedDomainObjectContainer<PluginOptions> plugins

  public GenerateProtoTask(Project project, String sourceSet) {
    final String outputBaseDir = "${project.buildDir}/generated/source/proto"
    def pluginsFactory = { String name ->
      return new PluginOptions(name, outputBaseDir, sourceSet)
    }
    builtins = project.container(PluginOptions, pluginsFactory)
    plugins = project.container(PluginOptions, pluginsFactory)
  }

  //===========================================================================
  //        Configuration methods
  //===========================================================================

  /**
   * Configures the protoc builtins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void plugins(Closure configureClosure) {
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
          includeDirs += dir
      } else {
          includeDirs += project.file(dir)
      }
  }

  /**
   * The container of command-line options for a protoc plugin or a built-in output.
   */
  public static class PluginOptions implements Named {
    private final ArrayList<String> options = new ArrayList<String>()
    private final String name

    public final String sourceSet

    private String outputDir

    public PluginOptions(String name, String outputBaseDir, String sourceSet) {
      this.name = name
      this.sourceSet = sourceSet
      this.outputDir = "${outputBaseDir}/${sourceSet}/${name}"
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

    /**
     * Returns the name of the source set that this task is tied with.  In
     * Android projects, it’s the variant name.  Useful when setting
     * outputDir.
     */
    public String getSourceSet() {
      return sourceSet
    }

    /**
     *  Sets the output dir of this builtin or plugin.  By default it’s
     *  '$buildDir/generated/source/proto/$sourceSet/$name'
     */
    public outputDir(String outputDir) {
      this.outputDir = outputDir
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

  @TaskAction
  def compile() {
    ToolsLocator tools = project.protobuf.tools
    Set<File> protoFiles = inputs.sourceFiles.files

    [builtins, plugins]*.each { plugin ->
      File outputDir = new File(plugin.outputDir)
      outputDir.mkdirs()
    }

    def dirs = includeDirs*.path.collect {"-I${it}"}
    logger.debug "ProtobufCompile using directories ${dirs}"
    logger.debug "ProtobufCompile using files ${protoFiles}"
    def cmd = [ tools.protoc.executablePath ]
    cmd.addAll(dirs)

    // Handle code generation built-ins
    builtins.each { builtin ->
      String outPrefix = makeOptionsPrefix(builtin.options)
      cmd += "--${builtin.name}_out=${outPrefix}${builtin.outputDir}"
    }

    // Handle code generation plugins
    plugins.each { plugin ->
      String name = plugin.name
      ExecutableLocator locator = tools.plugins.get(name)
      if (locator == null) {
        throw new GradleException("Codegen plugin ${name} not defined")
      }
      String path = locator.executablePath
      String pluginOutPrefix = makeOptionsPrefix(plugin.options)
      cmd += "--${name}_out=${pluginOutPrefix}${plugin.outputDir}"
      cmd += "--plugin=protoc-gen-${name}=${path}"
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
            throw new InvalidUserDataException(output)
        }
    }

}
