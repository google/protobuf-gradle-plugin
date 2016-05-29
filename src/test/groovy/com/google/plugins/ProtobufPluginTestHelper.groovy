import org.apache.commons.io.FileUtils

class ProtobufPluginTestHelper {

    void appendPluginClasspath(File buildFile) {
        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
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

            repositories {
                mavenCentral()
                jcenter()
            }
        """
    }

    void copyTestProject(String testProjectName, File projectDir) {
        def baseDir = new File(System.getProperty("user.dir"), "testProject")

        FileUtils.copyDirectory(baseDir, projectDir)

        def buildFile = new File(projectDir.path, "build.gradle")
        appendPluginClasspath(buildFile)
    }
}