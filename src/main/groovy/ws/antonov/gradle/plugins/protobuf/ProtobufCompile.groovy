package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.logging.LogLevel
import org.gradle.util.GUtil
import org.gradle.api.tasks.compile.AbstractCompile;

public class ProtobufCompile extends AbstractCompile {
    @Input
    def includeDirs = []

    public String getProtocPath() {
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
        def dirs = GUtil.join(getDefaultSource().srcDirs, " -I")
        logger.debug "ProtobufCompile using directories ${dirs}"
        logger.debug "ProtobufCompile using files ${getDefaultSource().getFiles()}"
        def cmd = [ getProtocPath() ]
        cmd.addAll(getDefaultSource().srcDirs*.path.collect {"-I${it}"})
        cmd.addAll(includeDirs*.path.collect {"-I${it}"})
        cmd += "--java_out=${getDestinationDir()}"
        cmd.addAll getDefaultSource().getFiles()
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
