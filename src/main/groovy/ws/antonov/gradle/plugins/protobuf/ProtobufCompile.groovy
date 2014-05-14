package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.logging.LogLevel
import org.gradle.util.CollectionUtils
import org.gradle.api.tasks.compile.AbstractCompile;

public class ProtobufCompile extends AbstractCompile {
    @Input
    def includeDirs = []

    public String getProtocPath() {
        return null
    }

    public Set getPlugins() {
        return null
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

    protected void compile() {
        //println "Compiling protos..."
        //println "${sourceSets.main.java.srcDirs}"
        //println project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).protobuf.class
        getDestinationDir().mkdir()
        def dirs = CollectionUtils.join(" -I", getSource().srcDirs)
        logger.debug "ProtobufCompile using directories ${dirs}"
        logger.debug "ProtobufCompile using files ${getSource().getFiles()}"
        def cmd = [ getProtocPath() ]
        cmd.addAll(getSource().srcDirs*.path.collect {"-I${it}"})
        cmd.addAll(includeDirs*.path.collect {"-I${it}"})
        cmd += "--java_out=${getDestinationDir()}"
        // Handle code generation plugins
        if (getPlugins()) {
            cmd.addAll(getPlugins().collect {
                def name = it
                if (it.indexOf(":") > 0) {
                    name = it.split(":")[0]
                }
                "--${name}_out=${getDestinationDir()}"
            })
            cmd.addAll(getPlugins().collect {
                if (it.indexOf(":") > 0) {
                    def values = it.split(":")
                    "--plugin=protoc-gen-${values[0]}=${values[1]}"
                } else {
                    "--plugin=protoc-gen-${it}=${project.projectDir}/protoc-gen-${it}"
                }
            })
        }

        cmd.addAll getSource().getFiles()
        logger.log(LogLevel.INFO, cmd.toString())
        def output = new StringBuffer()
        Process result = cmd.execute()
        result.consumeProcessOutput(output, output)
        result.waitFor()
        if (result.exitValue() == 0) {
            logger.log(LogLevel.INFO, output.toString())
        } else {
            throw new InvalidUserDataException(output.toString())
        }
    }
}
