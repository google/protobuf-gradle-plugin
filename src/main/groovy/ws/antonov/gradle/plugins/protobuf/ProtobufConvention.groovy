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
     *  List of code generation plugin names
     *  -- Each name will be transformed into '--plugin=protoc-gen-<name>' and '--<name>_out=<generatedFileDir>'
     *  -- Names have to be unique
     */
    def Set protobufCodeGenPlugins = Collections.emptySet()
}
