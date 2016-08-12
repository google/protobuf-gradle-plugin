import org.apache.commons.io.FileUtils

class ProtobufPluginTestHelper {

  static void appendPluginClasspath(File buildFile) {
    def pluginClasspathResource =
        ProtobufPluginTestHelper.class.classLoader.findResource("plugin-classpath.txt")
    if (pluginClasspathResource == null) {
      throw new IllegalStateException('Did not find plugin classpath resource, ' +
          'run `testClasses` build task.')
    }

    def pluginClasspath = pluginClasspathResource.readLines()
      .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
      .collect { "'$it'" }
      .join(", ")

    // Add the logic under test to the test build
    buildFile << """
        buildscript {
            dependencies {
                classpath files($pluginClasspath)
            }
        }
    """
  }

  static File prepareTestTempDir(String testProjectName) {
    File dir = new File(System.getProperty('user.dir'), 'build/tests/' + testProjectName)
    FileUtils.deleteDirectory(dir)
    FileUtils.forceMkdir(dir)
    return dir
  }

  static void copyTestProject(File projectDir, String testProjectName) {
    def baseDir = new File(System.getProperty("user.dir"), testProjectName)

    FileUtils.copyDirectory(baseDir, projectDir)

    def buildFile = new File(projectDir.path, "build.gradle")
    appendPluginClasspath(buildFile)
  }

  static void copyTestProjects(File projectDir, String... testProjectNames) {
    def settingsFile = new File(projectDir, 'settings.gradle')
    settingsFile.createNewFile()

    testProjectNames.each {
      copyTestProject(new File(projectDir.path, it), it)
      settingsFile << """
          include ':$it'
          project(':$it').projectDir = "\$rootDir/$it" as File
      """
    }

    def buildFile = new File(projectDir, 'build.gradle')
    buildFile.createNewFile()
  }
}
