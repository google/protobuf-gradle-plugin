package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Project

class ProtobufConvention {
    def ProtobufConvention(Project project) {
        extractedProtosDir = "${project.buildDir.path}/extracted-protos"
        generatedFileDir = "${project.buildDir}/generated-sources"
    }

    def String protocPath = "protoc"

    /**
     * Directory to extract proto files into
     */
    def String extractedProtosDir
		
    /**
     *	Directory to save java files to
     */
    def String generatedFileDir

    /**
     *  List of code generation plugin names and paths.
     *  -- Each item is a '<name>:<path>'
     *  -- Each name will be transformed into '--plugin=protoc-gen-<name>=<path>' and
     *     '--<name>_out=<generatedFileDir>'
     *  -- Names have to be unique
     */
    def Set protobufCodeGenPlugins = new HashSet()

    /**
     * List of native code generation plugins that is fetched from repositories.
     * -- Each itme is a '<name>:<plugin-groupId>:<plugin-artifactId>:<version>'
     * -- Each name will be transformed into '--plugin=protoc-gen-<name>=<path>' and
     *    '--<name>_out=<generatedFileDir>'
     * -- Names have to be unique
     */
    def Set protobufNativeCodeGenPluginDeps = Collections.emptySet()
}
