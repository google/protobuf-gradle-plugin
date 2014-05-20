package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Created by aantonov on 5/19/14.
 */
class ProtobufExtract extends DefaultTask {

    File extractedProtosDir

    String configName

    @TaskAction
    def extract() {
        project.configurations[configName].files.each { file ->
            if (file.path.endsWith('.proto')) {
                ant.copy(
                        file: file.path,
                        toDir: extractedProtosDir
                )
                //generateJavaTask.getSource().create(project.files(file))
            } else if (file.path.endsWith('.jar') || file.path.endsWith('.zip')) {
                ant.unzip(src: file.path, dest: extractedProtosDir)
            } else {
                def compression

                if (file.path.endsWith('.tar')) {
                    compression = 'none'
                } else
                if (file.path.endsWith('.tar.gz')) {
                    compression = 'gzip'
                } else if (file.path.endsWith('.tar.bz2')) {
                    compression = 'bzip2'
                } else {
                    throw new GradleException(
                            "Unsupported file type (${file.path}); handles only jar, tar, tar.gz & tar.bz2")
                }

                ant.untar(
                        src: file.path,
                        dest: extractedProtosDir,
                        compression: compression)
            }
        }
    }
}
