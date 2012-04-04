package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Project

class ProtobufConvention {
    def ProtobufConvention(Project project) {
        protoDirectory = "${project.buildDir.path}/downloaded-protos"
    }

    def String protocPath = "protoc"
    /**
     * Directory to download proto files to. Defaults to build/downloaded-protos
     */
    def String protoDirectory
}
