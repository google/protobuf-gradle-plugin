package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.LogLevel
import org.gradle.util.GUtil
import org.gradle.api.tasks.compile.AbstractCompile;

public class ProtobufCompile extends AbstractCompile {

    public String getProtocPath() {
        return null
    }

    protected void compile() {
        //println "Compiling protos..."
        //println "${sourceSets.main.java.srcDirs}"
        //println project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).protobuf.class
        getDestinationDir().mkdir()
        def dirs = GUtil.join(getDefaultSource().srcDirs, " -I")
        logger.debug "ProtobufCompile using directories ${dirs}"
        def files = GUtil.join(getDefaultSource().getFiles(), " ")
        logger.debug "ProtobufCompile using files ${files}"
        def cmd = "${getProtocPath()} -I${dirs} --java_out=${getDestinationDir()} ${files}"
        logger.log(LogLevel.INFO, cmd)
        Process result = cmd.execute()
        result.waitFor()
        def sbout = new StringBuffer()
        def sberr = new StringBuffer()
        result.consumeProcessOutput(sbout, sberr)
        if (result.exitValue() == 0) {
            logger.log(LogLevel.INFO, sbout.toString())
        } else {
            //logger.log(LogLevel.ERROR, sberr.toString())
            throw new InvalidUserDataException(sberr.toString())
        }
    }
}
