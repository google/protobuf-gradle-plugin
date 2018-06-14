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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.util.ConfigureUtil

/**
 * The task that compiles proto files into Java files.
 */
// TODO(zhangkun83): add per-plugin output dir reconfiguraiton.
public class GenerateProtoTask extends DefaultTask {
  // Windows CreateProcess has command line limit of 32768:
  // https://msdn.microsoft.com/en-us/library/windows/desktop/ms682425(v=vs.85).aspx
  static final int WINDOWS_CMD_LENGTH_LIMIT = 32760
  // Extra command line length when added an additional argument on Windows.
  // Two quotes and a space.
  static final int CMD_ARGUMENT_EXTRA_LENGTH = 3

  private final List includeDirs = []
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
  private boolean isTestVariant
  private FileResolver fileResolver

  /**
   * If true, will set the protoc flag
   * --descriptor_set_out="${outputBaseDir}/descriptor_set.desc"
   *
   * Default: false
   */
  boolean generateDescriptorSet

  /**
   * Configuration object for descriptor generation details.
   */
  public class DescriptorSetOptions {
    /**
     * If set, specifies an alternative location than the default for storing the descriptor
     * set.
     *
     * Default: null
     */
    GString path

    /**
     * If true, source information (comments, locations) will be included in the descriptor set.
     *
     * Default: false
     */
    boolean includeSourceInfo

    /**
     * If true, imports are included in the descriptor set, such that it is self-containing.
     *
     * Default: false
     */
    boolean includeImports
  }

  final DescriptorSetOptions descriptorSetOptions = new DescriptorSetOptions()

  private static enum State {
    INIT, CONFIG, FINALIZED
  }

  private State state = State.INIT

  private void checkInitializing() {
    Preconditions.checkState(state == State.INIT, 'Should not be called after initilization has finished')
  }

  private void checkCanConfig() {
    Preconditions.checkState(state == State.CONFIG || state == State.INIT,
        'Should not be called after configuration has finished')
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

  void setVariant(Object variant, boolean isTestVariant) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'variant should not be set in a Java project')
    this.variant = variant
    this.isTestVariant = isTestVariant
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

  void setFileResolver(FileResolver fileResolver) {
    checkInitializing()
    this.fileResolver = fileResolver
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

  boolean getIsTestVariant() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'isTestVariant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return isTestVariant
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
    Preconditions.checkState(
        variant.name == 'test' || buildType,
        'buildType is not set and task is not for local unit test variant')
    return buildType
  }

  FileResolver getFileResolver() {
    Preconditions.checkNotNull(fileResolver)
    return fileResolver
  }

  void doneInitializing() {
    Preconditions.checkState(state == State.INIT, "Invalid state: ${state}")
    state = State.CONFIG
  }

  void doneConfig() {
    Preconditions.checkState(state == State.CONFIG, "Invalid state: ${state}")
    state = State.FINALIZED
  }

  String getDescriptorPath() {
    if (!generateDescriptorSet) {
      throw new IllegalStateException(
          "requested descriptor path but descriptor generation is off")
    }
    return descriptorSetOptions.path != null
      ? descriptorSetOptions.path : "${outputBaseDir}/descriptor_set.desc"
  }

  public GenerateProtoTask() {
    builtins = project.container(PluginOptions)
    plugins = project.container(PluginOptions)
  }

  //===========================================================================
  //        Configuration methods
  //===========================================================================

  /**
   * Configures the protoc builtins in a closure, which will be manipulating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void builtins(Closure configureClosure) {
    checkCanConfig()
    ConfigureUtil.configure(configureClosure, builtins)
  }

  /**
   * Returns the container of protoc builtins.
   */
  public NamedDomainObjectContainer<PluginOptions> getBuiltins() {
    checkCanConfig()
    return builtins
  }

  /**
   * Configures the protoc plugins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void plugins(Closure configureClosure) {
    checkCanConfig()
    ConfigureUtil.configure(configureClosure, plugins)
  }

  /**
   * Returns the container of protoc plugins.
   */
  public NamedDomainObjectContainer<PluginOptions> getPlugins() {
    checkCanConfig()
    return plugins
  }

  /**
   * Returns true if the task has a plugin with the given name, false otherwise.
   */
  public boolean hasPlugin(String name) {
    return plugins.findByName(name) != null
  }

  /**
   * Add a directory to protoc's include path.
   */
  public void include(Object dir) {
    checkCanConfig()
    if (dir instanceof File) {
      includeDirs.add(dir)
    } else {
      includeDirs.add(project.file(dir))
    }
  }

  /**
   * Returns true if the Java source set or Android variant is test related.
   */
  public boolean getIsTest() {
    if (Utils.isAndroidProject(project)) {
      return isTestVariant
    }
    return Utils.isTest(sourceSet.name)
  }

  /**
   * The container of command-line options for a protoc plugin or a built-in output.
   */
  public static class PluginOptions implements Named {
    private final List<String> options = []
    private final String name
    private String outputSubDir

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

    /**
     * Set the output directory for this plugin, relative to {@link GenerateProtoTask#outputBaseDir}.
     */
    void setOutputSubDir(String outputSubDir) {
      this.outputSubDir = outputSubDir
    }

    /**
     * Returns the relative outputDir for this plugin.  If outputDir is not specified, name is used.
     */
    public String getOutputSubDir() {
      if (outputSubDir != null) {
        return outputSubDir
      }
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
  static String makeOptionsPrefix(List<String> options) {
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
    return "${outputBaseDir}/${plugin.outputSubDir}"
  }

  /**
   * Returns a {@code SourceDirectorySet} representing the generated source
   * directories.
   */
  SourceDirectorySet getOutputSourceDirectorySet() {
    Preconditions.checkNotNull(fileResolver)
    SourceDirectorySet srcSet = new DefaultSourceDirectorySet(
        "generate-proto-" + name, this.fileResolver, new DefaultDirectoryFileTreeFactory())
    builtins.each { builtin ->
      srcSet.srcDir new File(getOutputDir(builtin))
    }
    plugins.each { plugin ->
      srcSet.srcDir new File(getOutputDir(plugin))
    }
    return srcSet
  }

  @TaskAction
  void compile() {
    Preconditions.checkState(state == State.FINALIZED, 'doneConfig() has not been called')

    ToolsLocator tools = project.protobuf.tools
    // Sort to ensure generated descriptors have a canonical representation
    // to avoid triggering unnecessary rebuilds downstream
    List<File> protoFiles = inputs.sourceFiles.files.sort()

    [builtins, plugins]*.each { plugin ->
      File outputDir = new File(getOutputDir(plugin))
      outputDir.mkdirs()
    }

    List<String> dirs = includeDirs*.path.collect { "-I${it}" }
    logger.debug "ProtobufCompile using directories ${dirs}"
    logger.debug "ProtobufCompile using files ${protoFiles}"
    List<String> baseCmd = [ tools.protoc.path ]
    baseCmd.addAll(dirs)

    // Handle code generation built-ins
    builtins.each { builtin ->
      String outPrefix = makeOptionsPrefix(builtin.options)
      baseCmd += "--${builtin.name}_out=${outPrefix}${getOutputDir(builtin)}"
    }

    // Handle code generation plugins
    plugins.each { plugin ->
      String name = plugin.name
      ExecutableLocator locator = tools.plugins.findByName(name)
      if (locator == null) {
        throw new GradleException("Codegen plugin ${name} not defined")
      }
      String pluginOutPrefix = makeOptionsPrefix(plugin.options)
      baseCmd += "--${name}_out=${pluginOutPrefix}${getOutputDir(plugin)}"
      baseCmd += "--plugin=protoc-gen-${name}=${locator.path}"
    }

    if (generateDescriptorSet) {
      String path = getDescriptorPath()
      // Ensure that the folder for the descriptor exists;
      // the user may have set it to point outside an existing tree
      File folder = new File(path).parentFile
      if (!folder.exists()) {
        folder.mkdirs()
      }
      baseCmd += "--descriptor_set_out=${path}"
      if (descriptorSetOptions.includeImports) {
        baseCmd += "--include_imports"
      }
      if (descriptorSetOptions.includeSourceInfo) {
        baseCmd += "--include_source_info"
      }
    }

    List<List<String>> cmds = generateCmds(baseCmd, protoFiles, getCmdLengthLimit())
    for (List<String> cmd : cmds) {
      compileFiles(cmd)
    }
  }

  private void compileFiles(List<String> cmd) {
    logger.log(LogLevel.INFO, cmd.toString())

    StringBuffer stdout = new StringBuffer()
    StringBuffer stderr = new StringBuffer()
    Process result = cmd.execute()
    result.waitForProcessOutput(stdout, stderr)
    String output = "protoc: stdout: ${stdout}. stderr: ${stderr}"
    if (result.exitValue() == 0) {
      logger.log(LogLevel.INFO, output)
    } else {
      throw new GradleException(output)
    }
  }

  static List<List<String>> generateCmds(List<String> baseCmd, List<File> protoFiles, int cmdLengthLimit) {
    List<List<String>> cmds = []
    if (!protoFiles.isEmpty()) {
      int baseCmdLength = baseCmd.sum { it.length() + CMD_ARGUMENT_EXTRA_LENGTH }
      List<String> currentArgs = []
      int currentArgsLength = 0
      for (File proto: protoFiles) {
        String protoFileName = proto
        int currentFileLength = protoFileName.length() + CMD_ARGUMENT_EXTRA_LENGTH
        // Check if appending the next proto string will overflow the cmd length limit
        if (baseCmdLength + currentArgsLength + currentFileLength > cmdLengthLimit) {
          // Add the current cmd before overflow
          cmds.add(baseCmd + currentArgs)
          currentArgs.clear()
          currentArgsLength = 0
        }
        // Append the proto file to the args
        currentArgs.add(protoFileName)
        currentArgsLength += currentFileLength
      }
      // Add the last cmd for execution
      cmds.add(baseCmd + currentArgs)
    }
    return cmds
  }

  static int getCmdLengthLimit() {
    return getCmdLengthLimit(System.getProperty("os.name"))
  }

  static int getCmdLengthLimit(String os) {
    if (os != null && os.toLowerCase(Locale.ROOT).indexOf("win") > -1) {
      return WINDOWS_CMD_LENGTH_LIMIT
    }
    return Integer.MAX_VALUE
  }

}
