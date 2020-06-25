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
import groovy.transform.CompileDynamic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.util.ConfigureUtil

import javax.annotation.Nullable
import javax.inject.Inject

/**
 * The task that compiles proto files into Java files.
 */
// TODO(zhangkun83): add per-plugin output dir reconfiguraiton.
@CompileDynamic
@CacheableTask
public abstract class GenerateProtoTask extends DefaultTask {
  // Windows CreateProcess has command line limit of 32768:
  // https://msdn.microsoft.com/en-us/library/windows/desktop/ms682425(v=vs.85).aspx
  static final int WINDOWS_CMD_LENGTH_LIMIT = 32760
  // Extra command line length when added an additional argument on Windows.
  // Two quotes and a space.
  static final int CMD_ARGUMENT_EXTRA_LENGTH = 3

  // include dirs are passed to the '-I' option of protoc.  They contain protos
  // that may be "imported" from the source protos, but will not be compiled.
  private final ConfigurableFileCollection includeDirs = objectFactory.fileCollection()
  // source files are proto files that will be compiled by protoc
  private final ConfigurableFileCollection sourceFiles = objectFactory.fileCollection()
  private final NamedDomainObjectContainer<PluginOptions> builtins = objectFactory.domainObjectContainer(PluginOptions)
  private final NamedDomainObjectContainer<PluginOptions> plugins = objectFactory.domainObjectContainer(PluginOptions)

  // These fields are set by the Protobuf plugin only when initializing the
  // task.  Ideally they should be final fields, but Gradle task cannot have
  // constructor arguments. We use the initializing flag to prevent users from
  // accidentally modifying them.
  private String outputBaseDir
  // Tags for selectors inside protobuf.generateProtoTasks; do not serialize with Gradle configuration caching
  @SuppressWarnings("UnnecessaryTransientModifier") // It is not necessary for task to implement Serializable
  transient private SourceSet sourceSet
  @SuppressWarnings("UnnecessaryTransientModifier") // It is not necessary for task to implement Serializable
  transient private Object variant
  private ImmutableList<String> flavors
  private String buildType
  private boolean isTestVariant
  private FileResolver fileResolver
  private final Provider<String> variantName = providerFactory.provider { variant.name }
  private final Provider<Boolean> isAndroidProject = providerFactory.provider { Utils.isAndroidProject(project) }
  private final Provider<Boolean> isTestProvider = providerFactory.provider {
    if (Utils.isAndroidProject(project)) {
      return isTestVariant
    }
    return Utils.isTest(sourceSet.name)
  }

  /**
   * If true, will set the protoc flag
   * --descriptor_set_out="${outputBaseDir}/descriptor_set.desc"
   *
   * Default: false
   */
  @Internal("Handled as input via getDescriptorSetOptionsForCaching()")
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
    @Nullable
    @Optional
    @OutputFile
    String path

    /**
     * If true, source information (comments, locations) will be included in the descriptor set.
     *
     * Default: false
     */
    @Input
    boolean includeSourceInfo

    /**
     * If true, imports are included in the descriptor set, such that it is self-containing.
     *
     * Default: false
     */
    @Input
    boolean includeImports
  }

  @Internal("Handled as input via getDescriptorSetOptionsForCaching()")
  final DescriptorSetOptions descriptorSetOptions = new DescriptorSetOptions()

  /**
   * If true, will set the protoc flag --experimental_allow_proto3_optional
   *
   * Default: false
   */
  @Input
  boolean experimentalAllowProto3Optional

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

  void setOutputBaseDir(String outputBaseDir) {
    checkInitializing()
    Preconditions.checkState(this.outputBaseDir == null, 'outputBaseDir is already set')
    this.outputBaseDir = outputBaseDir
  }

  @OutputDirectory
  String getOutputBaseDir() {
    return outputBaseDir
  }

  void setSourceSet(SourceSet sourceSet) {
    checkInitializing()
    Preconditions.checkState(!isAndroidProject.get(),
        'sourceSet should not be set in an Android project')
    this.sourceSet = sourceSet
  }

  void setVariant(Object variant, boolean isTestVariant) {
    checkInitializing()
    Preconditions.checkState(isAndroidProject.get(),
        'variant should not be set in a Java project')
    this.variant = variant
    this.isTestVariant = isTestVariant
  }

  void setFlavors(ImmutableList<String> flavors) {
    checkInitializing()
    Preconditions.checkState(isAndroidProject.get(),
        'flavors should not be set in a Java project')
    this.flavors = flavors
  }

  void setBuildType(String buildType) {
    checkInitializing()
    Preconditions.checkState(isAndroidProject.get(),
        'buildType should not be set in a Java project')
    this.buildType = buildType
  }

  void setFileResolver(FileResolver fileResolver) {
    checkInitializing()
    this.fileResolver = fileResolver
  }

  @Internal("Inputs tracked in getSourceFiles()")
  SourceSet getSourceSet() {
    Preconditions.checkState(!isAndroidProject.get(),
        'sourceSet should not be used in an Android project')
    Preconditions.checkNotNull(sourceSet, 'sourceSet is not set')
    return sourceSet
  }

  @SkipWhenEmpty
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  FileCollection getSourceFiles() {
    return sourceFiles
  }

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  FileCollection getIncludeDirs() {
    return includeDirs
  }

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  Object getVariant() {
    Preconditions.checkState(isAndroidProject.get(),
        'variant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return variant
  }

  @Internal("Input captured by getAlternativePaths()")
  abstract Property<ExecutableLocator> getProtocLocator()

  @Internal("Input captured by getAlternativePaths(), this is used to query alternative path by locator name.")
  abstract MapProperty<String, FileCollection> getLocatorToAlternativePathsMapping()

  @InputFiles
  @PathSensitive(PathSensitivity.NONE)
  ConfigurableFileCollection getAlternativePaths() {
    return objectFactory.fileCollection().from(getLocatorToAlternativePathsMapping().get().values())
  }

  @Internal("Input captured by getAlternativePaths()")
  abstract MapProperty<String, ExecutableLocator> getPluginsExecutableLocators()

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  Provider<Boolean> getIsAndroidProject() {
    return isAndroidProject
  }

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  Provider<String> getVariantName() {
    return variantName
  }

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  boolean getIsTestVariant() {
    Preconditions.checkState(isAndroidProject.get(),
        'isTestVariant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return isTestVariant
  }

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  ImmutableList<String> getFlavors() {
    Preconditions.checkState(isAndroidProject.get(),
        'flavors should not be used in a Java project')
    Preconditions.checkNotNull(flavors, 'flavors is not set')
    return flavors
  }

  @Internal("Not an actual input to the task, only used to find tasks belonging to a variant")
  String getBuildType() {
    Preconditions.checkState(isAndroidProject.get(),
        'buildType should not be used in a Java project')
    Preconditions.checkState(
        variantName.get() == 'test' || buildType,
        'buildType is not set and task is not for local unit test variant')
    return buildType
  }

  void doneInitializing() {
    Preconditions.checkState(state == State.INIT, "Invalid state: ${state}")
    state = State.CONFIG
  }

  void doneConfig() {
    Preconditions.checkState(state == State.CONFIG, "Invalid state: ${state}")
    state = State.FINALIZED
  }

  @Internal("Tracked as an input via getDescriptorSetOptionsForCaching()")
  String getDescriptorPath() {
    if (!generateDescriptorSet) {
      throw new IllegalStateException(
          "requested descriptor path but descriptor generation is off")
    }
    return descriptorSetOptions.path != null ? descriptorSetOptions.path : "${outputBaseDir}/descriptor_set.desc"
  }

  @Inject
  abstract ProviderFactory getProviderFactory()

  @Inject
  abstract ObjectFactory getObjectFactory()

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
  @Internal("Tracked as an input via getBuiltinsForCaching()")
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
  @Internal("Tracked as an input via getPluginsForCaching()")
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
  public void addIncludeDir(FileCollection dir) {
    checkCanConfig()
    includeDirs.from(dir)
  }

  /**
   * Add a collection of proto source files to be compiled.
   */
  public void addSourceFiles(FileCollection files) {
    checkCanConfig()
    sourceFiles.from(files)
  }

  /**
   * Returns true if the Java source set or Android variant is test related.
   */
  @Input
  public boolean getIsTest() {
    return isTestProvider.get()
  }

  @Internal("Already captured with getIsTest()")
  Provider<Boolean> getIsTestProvider() {
    return isTestProvider
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

    @Input
    public List<String> getOptions() {
      return options
    }

    /**
     * Returns the name of the plugin or builtin.
     */
    @Input
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
    @Input
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

  String getOutputDir(PluginOptions plugin) {
    return "${outputBaseDir}/${plugin.outputSubDir}"
  }

  /**
   * Returns a {@code SourceDirectorySet} representing the generated source
   * directories.
   */
  @Internal
  SourceDirectorySet getOutputSourceDirectorySet() {
    String srcSetName = "generate-proto-" + name
    SourceDirectorySet srcSet
    srcSet = objectFactory.sourceDirectorySet(srcSetName, srcSetName)
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

    // Sort to ensure generated descriptors have a canonical representation
    // to avoid triggering unnecessary rebuilds downstream
    List<File> protoFiles = sourceFiles.files.sort()

    [builtins, plugins]*.each { plugin ->
      File outputDir = new File(getOutputDir(plugin))
      outputDir.mkdirs()
    }

    // The source directory designated from sourceSet may not actually exist on disk.
    // "include" it only when it exists, so that Gradle and protoc won't complain.
    List<String> dirs = includeDirs.filter { it.exists() }*.path.collect { "-I${it}" }
    logger.debug "ProtobufCompile using directories ${dirs}"
    logger.debug "ProtobufCompile using files ${protoFiles}"

    String protocPath = computeExecutablePath(protocLocator.get())
    List<String> baseCmd = [ protocPath ]
    baseCmd.addAll(dirs)

    // Handle code generation built-ins
    builtins.each { builtin ->
      String outPrefix = makeOptionsPrefix(builtin.options)
      baseCmd += "--${builtin.name}_out=${outPrefix}${getOutputDir(builtin)}"
    }

    Map<String, ExecutableLocator> executableLocations = pluginsExecutableLocators.get()
    // Handle code generation plugins
    plugins.each { plugin ->
      String name = plugin.name
      ExecutableLocator locator = executableLocations.get(name)
      if (locator != null) {
        baseCmd += "--plugin=protoc-gen-${name}=${computeExecutablePath(locator)}"
      } else {
        logger.warn "protoc plugin '${name}' not defined. Trying to use 'protoc-gen-${name}' from system path"
      }
      String pluginOutPrefix = makeOptionsPrefix(plugin.options)
      baseCmd += "--${name}_out=${pluginOutPrefix}${getOutputDir(plugin)}"
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

    if (experimentalAllowProto3Optional) {
      baseCmd += "--experimental_allow_proto3_optional"
    }

    List<List<String>> cmds = generateCmds(baseCmd, protoFiles, getCmdLengthLimit())
    for (List<String> cmd : cmds) {
      compileFiles(cmd)
    }
  }

  /**
   * Used to expose inputs to Gradle, not to be called directly.
   */
  @Optional
  @Nested
  protected DescriptorSetOptions getDescriptorSetOptionsForCaching() {
    return generateDescriptorSet ? descriptorSetOptions : null
  }

  /**
   * Used to expose inputs to Gradle, not to be called directly.
   */
  @Nested
  protected Collection<PluginOptions> getBuiltinsForCaching() {
    return builtins
  }

  /**
   * Used to expose inputs to Gradle, not to be called directly.
   */
  @Nested
  protected Collection<PluginOptions> getPluginsForCaching() {
    return plugins
  }

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

  protected String computeExecutablePath(ExecutableLocator locator) {
    if (locator.path != null) {
      return locator.path
    }
    File file = locatorToAlternativePathsMapping.getting(locator.name).get().singleFile
    if (!file.canExecute() && !file.setExecutable(true)) {
      throw new GradleException("Cannot set ${file} as executable")
    }
    logger.info("Resolved artifact: ${file}")
    return file.path
  }
}
