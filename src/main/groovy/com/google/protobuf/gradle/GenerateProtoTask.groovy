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

import static java.nio.charset.StandardCharsets.US_ASCII

import com.google.protobuf.gradle.internal.DefaultGenerateProtoTaskSpec
import com.google.protobuf.gradle.tasks.GenerateProtoTaskSpec
import com.google.protobuf.gradle.tasks.PluginSpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * The task that compiles proto files into Java files.
 */
// TODO(zhangkun83): add per-plugin output dir reconfiguraiton.
@CompileStatic
@CacheableTask
abstract class GenerateProtoTask extends DefaultTask {
  // Windows CreateProcess has command line limit of 32768:
  // https://msdn.microsoft.com/en-us/library/windows/desktop/ms682425(v=vs.85).aspx
  static final int WINDOWS_CMD_LENGTH_LIMIT = 32760
  // Most OSs impose some kind of command length limit.
  // Rather than account for all cases, pick a reasonable default of 64K.
  static final int DEFAULT_CMD_LENGTH_LIMIT = 65536
  // Extra command line length when added an additional argument on Windows.
  // Two quotes and a space.
  static final int CMD_ARGUMENT_EXTRA_LENGTH = 3
  private static final String JAR_SUFFIX = ".jar"

  private final CopyActionFacade copyActionFacade = CopyActionFacade.Loader.create(project, objectFactory)
  // include dirs are passed to the '-I' option of protoc.  They contain protos
  // that may be "imported" from the source protos, but will not be compiled.
  private final ConfigurableFileCollection includeDirs = objectFactory.fileCollection()
  // source files are proto files that will be compiled by protoc
  private final ConfigurableFileCollection sourceDirs = objectFactory.fileCollection()
  private final ProjectLayout projectLayout = project.layout
  private final ToolsLocator toolsLocator = project.extensions.findByType(ProtobufExtension).tools

  // These fields are set by the Protobuf plugin only when initializing the
  // task.  Ideally they should be final fields, but Gradle task cannot have
  // constructor arguments. We use the initializing flag to prevent users from
  // accidentally modifying them.
  private Provider<String> outputBaseDir

  @SuppressWarnings("AbstractClassWithPublicConstructor") // required to configure properties convention values
  GenerateProtoTask() {
    this.spec.convention(new DefaultGenerateProtoTaskSpec(this.project.objects))
  }

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
      int baseCmdLength = baseCmd.sum { it.length() + CMD_ARGUMENT_EXTRA_LENGTH } as int
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
    return isWindows(os) ? WINDOWS_CMD_LENGTH_LIMIT : DEFAULT_CMD_LENGTH_LIMIT
  }

  static boolean isWindows(String os) {
    return os != null && os.toLowerCase(Locale.ROOT).indexOf("win") > -1
  }

  static boolean isWindows() {
    return isWindows(System.getProperty("os.name"))
  }

  static String escapePathUnix(String path) {
    return path.replace("'", "'\\''")
  }

  static String escapePathWindows(String path) {
    String escapedPath = path.replace("%", "%%")
    return escapedPath.endsWith("\\") ? escapedPath + "\\" : escapedPath
  }

  static void mkdirsForFile(File outputFile) throws IOException {
    if (!outputFile.getParentFile().isDirectory() && !outputFile.getParentFile().mkdirs()) {
      throw new IOException("unable to make directories for file: " + outputFile.getCanonicalPath())
    }
  }

  static void setExecutableOrFail(File outputFile) throws IOException {
    if (!outputFile.setExecutable(true)) {
      outputFile.delete()
      throw new IOException("unable to set file as executable: " + outputFile.getCanonicalPath())
    }
  }

  static String computeJavaExePath(boolean isWindows) throws IOException {
    File java = new File(System.getProperty("java.home"), isWindows ? "bin/java.exe" : "bin/java")
    if (!java.exists()) {
      throw new IOException("Could not find java executable at " + java.path)
    }
    return java.path
  }

  void setOutputBaseDir(Provider<String> outputBaseDir) {
    Preconditions.checkState(this.outputBaseDir == null, 'outputBaseDir is already set')
    this.outputBaseDir = outputBaseDir
  }

  @OutputDirectory
  String getOutputBaseDir() {
    return outputBaseDir.get()
  }

  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  @InputFiles
  FileCollection getSourceDirs() {
    return sourceDirs
  }

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  FileCollection getIncludeDirs() {
    return includeDirs
  }

  /**
   * Not for external use. Used to expose inputs to Gradle.
   *
   * For each protoc and code gen plugin defined by an artifact specification, this list will contain a String with the
   * group, artifact, and version, as long as the version is a stable release version.
   *
   * Giving this as an input to the task allows gradle to ignore the OS classifier and use cached outputs generated from
   * different operating systems since the expectation is that different operating systems will produce the same
   * generated code.
   */
  @Input
  Provider<List<String>> getReleaseArtifacts() {
    providerFactory.provider {
      releaseExecutableLocators.collect { it.simplifiedArtifactName }
    }
  }

  /** Not for external use. Used to expose inputs to Gradle. */
  @InputFiles
  @PathSensitive(PathSensitivity.NONE)
  FileCollection getExecutables() {
    Provider<List> executables = providerFactory.provider {
      List<ExecutableLocator> release = releaseExecutableLocators
      allExecutableLocators.findAll { !release.contains(it) }
        .collect { it.path != null ? it.path : it.artifactFiles }
    }
    objectFactory.fileCollection().from(executables)
  }

  private List<ExecutableLocator> getReleaseExecutableLocators() {
    allExecutableLocators.findAll { it.path == null && !it.simplifiedArtifactName.endsWith ("-SNAPSHOT") }
  }

  private List<ExecutableLocator> getAllExecutableLocators() {
    [toolsLocator.protoc] + requireSpec().plugins.collect { PluginSpec it -> toolsLocator.plugins.getByName(it.name) }
  }

  @Internal("Tracked as an input via getDescriptorSetOptionsForCaching()")
  String getDescriptorPath() {
    GenerateProtoTaskSpec spec = requireSpec()
    if (!spec.generateDescriptorSet) {
      throw new IllegalStateException(
          "requested descriptor path but descriptor generation is off")
    }
    return spec.descriptorSetOptions.path != null ? spec.descriptorSetOptions.path
      : "${outputBaseDir.get()}/descriptor_set.desc"
  }

  @Inject
  abstract ProviderFactory getProviderFactory()

  @Inject
  abstract ObjectFactory getObjectFactory()

  @Nested
  abstract Property<GenerateProtoTaskSpec> getSpec()

  //===========================================================================
  //        Configuration methods
  //===========================================================================

  /**
   * Add a directory to protoc's include path.
   */
  public void addIncludeDir(FileCollection dir) {
    includeDirs.from(dir)
  }

  /**
   * Add a collection of proto source files to be compiled.
   */
  public void addSourceDirs(FileCollection dirs) {
    sourceDirs.from(dirs)
  }

  //===========================================================================
  //    protoc invocation logic
  //===========================================================================

  String getOutputDir(PluginSpec plugin) {
    return "${outputBaseDir.get()}/${plugin.outputSubDir}"
  }

  /**
   * Returns a {@code SourceDirectorySet} representing the generated source
   * directories.
   */
  @Internal
  @Deprecated
  SourceDirectorySet getOutputSourceDirectorySet() {
    String srcSetName = "generate-proto-" + name
    SourceDirectorySet srcSet
    srcSet = objectFactory.sourceDirectorySet(srcSetName, srcSetName)
    srcSet.srcDirs objectFactory.fileCollection().builtBy(this).from(providerFactory.provider {
      getOutputSourceDirectories()
    })
    return srcSet
  }

  @Internal
  @PackageScope
  Collection<File> getOutputSourceDirectories() {
    GenerateProtoTaskSpec spec = requireSpec()

    Collection<File> srcDirs = []
    spec.builtins.each { builtin ->
      File dir = new File(getOutputDir(builtin))
      if (!dir.name.endsWith(".zip") && !dir.name.endsWith(".jar")) {
        srcDirs.add(dir)
      }
    }
    spec.plugins.each { plugin ->
      File dir = new File(getOutputDir(plugin))
      if (!dir.name.endsWith(".zip") && !dir.name.endsWith(".jar")) {
        srcDirs.add(dir)
      }
    }
    return srcDirs
  }

  @TaskAction
  void compile() {
    GenerateProtoTaskSpec spec = requireSpec()

    copyActionFacade.delete { DeleteSpec deleteSpec ->
      deleteSpec.delete(outputBaseDir)
    }
    // Sort to ensure generated descriptors have a canonical representation
    // to avoid triggering unnecessary rebuilds downstream
    List<File> protoFiles = sourceDirs.asFileTree.files.sort()

    [spec.builtins, spec.plugins]*.forEach { PluginSpec plugin ->
      String outputPath = getOutputDir(plugin)
      File outputDir = new File(outputPath)
      // protoc is capable of output generated files directly to a JAR file
      // or ZIP archive if the output location ends with .jar/.zip
      if (outputPath.endsWith(".jar") || outputPath.endsWith(".zip")) {
        outputDir = outputDir.getParentFile()
      }
      outputDir.mkdirs()
    }

    // The source directory designated from sourceSet may not actually exist on disk.
    // "include" it only when it exists, so that Gradle and protoc won't complain.
    List<String> dirs = includeDirs.filter { File it -> it.exists() }*.path
        .collect { "-I${it}".toString() }
    logger.debug "ProtobufCompile using directories ${dirs}"
    logger.debug "ProtobufCompile using files ${protoFiles}"

    String protocPath = computeExecutablePath(toolsLocator.protoc)
    List<String> baseCmd = [ protocPath ]
    baseCmd.addAll(dirs)

    // Handle code generation built-ins
    spec.builtins.each { builtin ->
      String outPrefix = makeOptionsPrefix(builtin.options)
      baseCmd += "--${builtin.name}_out=${outPrefix}${getOutputDir(builtin)}".toString()
    }

    Map<String, ExecutableLocator> executableLocations = toolsLocator.plugins.asMap
    // Handle code generation plugins
    spec.plugins.each { PluginSpec plugin ->
      String name = plugin.name
      ExecutableLocator locator = executableLocations.get(name)
      if (locator != null) {
        baseCmd += "--plugin=protoc-gen-${name}=${computeExecutablePath(locator)}".toString()
      } else {
        logger.warn "protoc plugin '${name}' not defined. Trying to use 'protoc-gen-${name}' from system path"
      }
      String pluginOutPrefix = makeOptionsPrefix(plugin.options)
      baseCmd += "--${name}_out=${pluginOutPrefix}${getOutputDir(plugin)}".toString()
    }

    if (spec.generateDescriptorSet) {
      String path = getDescriptorPath()
      // Ensure that the folder for the descriptor exists;
      // the user may have set it to point outside an existing tree
      File folder = new File(path).parentFile
      if (!folder.exists()) {
        folder.mkdirs()
      }
      baseCmd += "--descriptor_set_out=${path}".toString()
      if (spec.descriptorSetOptions.includeImports) {
        baseCmd += "--include_imports"
      }
      if (spec.descriptorSetOptions.includeSourceInfo) {
        baseCmd += "--include_source_info"
      }
    }

    List<List<String>> cmds = generateCmds(baseCmd, protoFiles, getCmdLengthLimit())
    for (List<String> cmd : cmds) {
      compileFiles(cmd)
    }
  }

  private GenerateProtoTaskSpec requireSpec() {
    return spec.get()
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
      return locator.path.endsWith(JAR_SUFFIX) ? createJarTrampolineScript(locator.path) : locator.path
    }
    File file = locator.artifactFiles.singleFile
    if (file.name.endsWith(JAR_SUFFIX)) {
      return createJarTrampolineScript(file.getAbsolutePath())
    }

    if (!file.canExecute() && !file.setExecutable(true)) {
      throw new GradleException("Cannot set ${file} as executable")
    }
    logger.info("Resolved artifact: ${file}")
    return file.path
  }

  /**
   * protoc only supports plugins that are a single self contained executable file. For .jar files create a trampoline
   * script to execute the jar file. Assume the jar is a "fat jar" or "uber jar" and don't attempt any artifact
   * resolution.
   * @param jarAbsolutePath Absolute path to the .jar file.
   * @return The absolute path to the trampoline executable script.
   */
  private String createJarTrampolineScript(String jarAbsolutePath) {
    assert jarAbsolutePath.endsWith(JAR_SUFFIX)
    boolean isWindows = isWindows()
    String jarFileName = new File(jarAbsolutePath).getName()
    if (jarFileName.length() <= JAR_SUFFIX.length()) {
      throw new GradleException(".jar protoc plugin path '${jarAbsolutePath}' has no file name")
    }
    File scriptExecutableFile = new File("${projectLayout.buildDirectory.get()}/scripts/" +
            jarFileName[0..(jarFileName.length() - JAR_SUFFIX.length() - 1)] + "-${getName()}-trampoline." +
            (isWindows ? "bat" : "sh"))
    try {
      mkdirsForFile(scriptExecutableFile)
      String javaExe = computeJavaExePath(isWindows)
      // Rewrite the trampoline file unconditionally (even if it already exists) in case the dependency or versioning
      // changes we don't need to detect the delta (and the file content is cheap to re-generate).
      String trampoline = isWindows ?
              "@ECHO OFF\r\n\"${escapePathWindows(javaExe)}\" -jar \"${escapePathWindows(jarAbsolutePath)}\" %*\r\n" :
              "#!/bin/sh\nexec '${escapePathUnix(javaExe)}' -jar '${escapePathUnix(jarAbsolutePath)}' \"\$@\"\n"
      scriptExecutableFile.write(trampoline, US_ASCII.name())
      setExecutableOrFail(scriptExecutableFile)
      logger.info("Resolved artifact jar: ${jarAbsolutePath}. Created trampoline file: ${scriptExecutableFile}")
      return scriptExecutableFile.path
    } catch (IOException e) {
      throw new GradleException("Unable to generate trampoline for .jar protoc plugin", e)
    }
  }
}
