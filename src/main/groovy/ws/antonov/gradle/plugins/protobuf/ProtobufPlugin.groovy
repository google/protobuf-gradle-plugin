package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;

class ProtobufPlugin implements Plugin<Project> {
    void apply(final Project project) {
        project.apply plugin: 'java'

        project.convention.plugins.protobuf = new ProtobufConvention(project);
        project.sourceSets.all { SourceSet sourceSet ->
            ProtobufSourceSet protobufSourceSet = new ProtobufSourceSet(sourceSet.displayName, project.fileResolver)
            sourceSet.convention.plugins.put('protobuf', protobufSourceSet)
            protobufSourceSet.protobuf.srcDir("src/${sourceSet.name}/protobuf")
            def generateJavaTaskName = sourceSet.getTaskName('generate', 'proto')
            ProtobufCompile generateJavaTask = project.tasks.add(generateJavaTaskName, ProtobufCompile)
            configureForSourceSet project, sourceSet, generateJavaTask
            
            def protobufConfigName = (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "protobuf" : sourceSet.getName() + "Protobuf")
            project.configurations.add(protobufConfigName) {
                visible = false
                transitive = false
                extendsFrom = []
            }
            def downloadProtosTaskName = sourceSet.getTaskName('download', 'proto')
            def downloadProtosTask = project.tasks.add(downloadProtosTaskName) {
                description = 'Downloads proto tar specified by \'protos\' configuration'
                actions = [ 
                {
                    project.configurations[protobufConfigName].files.each { file ->
                        ant.untar(src: file.path, dest: project.protoDirectory + "/" + sourceSet.getName(), compression: 'gzip')
                    }
                } as Action
                ]
            }
            generateJavaTask.dependsOn(downloadProtosTask)
            generateJavaTask.getSource().srcDir project.protoDirectory + "/" + sourceSet.getName()
            
            sourceSet.java.srcDir getGeneratedSourceDir(project, sourceSet)
            String compileJavaTaskName = sourceSet.getCompileTaskName("java");
            Task compileJavaTask = project.tasks.getByName(compileJavaTaskName);
            compileJavaTask.dependsOn(generateJavaTask)
        }

    }
    
    void configureForSourceSet(Project project, final SourceSet sourceSet, ProtobufCompile compile) {
        def final defaultSource = new DefaultSourceDirectorySet("${sourceSet.displayName} Protobuf source", project.fileResolver);
        defaultSource.include("**/*.proto")
        defaultSource.filter.include("**/*.proto")
        defaultSource.srcDir("src/${sourceSet.name}/proto")
        compile.conventionMapping.map("classpath") {
            return sourceSet.getCompileClasspath()
        }
        compile.conventionMapping.map("protocPath") {
            return project.convention.plugins.protobuf.protocPath
        }
        compile.conventionMapping.map('source') {
            return defaultSource
        }
        compile.conventionMapping.map('destinationDir') {
            return new File(getGeneratedSourceDir(project, sourceSet))
        }
    }

    private getGeneratedSourceDir(Project project, SourceSet sourceSet) {
        def generatedSourceDir = 'generated-sources'
        //if (sourceSet.name != SourceSet.MAIN_SOURCE_SET_NAME)
        //    generatedSourceDir = "${sourceSet.name}-generated-sources"
        return "${project.buildDir}/${generatedSourceDir}/${sourceSet.name}"
    }

}

class ProtobufSourceSet {
    SourceDirectorySet protobuf
    def ProtobufSourceSet(String displayName, FileResolver fileResolver) {
        protobuf = new DefaultSourceDirectorySet("${displayName} Protobuf source", fileResolver)
    }
}
