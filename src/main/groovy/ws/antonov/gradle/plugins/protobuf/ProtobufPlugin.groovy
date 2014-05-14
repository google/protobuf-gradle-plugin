package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.GradleException
import org.gradle.tooling.UnsupportedVersionException

class ProtobufPlugin implements Plugin<Project> {
    void apply(final Project project) {
        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1) < "1" || gv.group(2) < "12") {
            //throw new UnsupportedVersionException
            println("You are using Gradle ${project.gradle.gradleVersion}: This version of the protobuf plugin requires minimum Gradle version 1.12")
        }

        project.apply plugin: 'java'

        project.convention.plugins.protobuf = new ProtobufConvention(project);
        project.sourceSets.all { SourceSet sourceSet ->
            def generateJavaTaskName = sourceSet.getTaskName('generate', 'proto')
            ProtobufCompile generateJavaTask = project.tasks.create(generateJavaTaskName, ProtobufCompile)
            generateJavaTask.description = "Compiles Proto source '${sourceSet.name}:proto'"
            configureForSourceSet project, sourceSet, generateJavaTask
            
            def protobufConfigName = (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "protobuf" : sourceSet.getName() + "Protobuf")
            project.configurations.create(protobufConfigName) {
                visible = false
                transitive = false
                extendsFrom = []
            }
            def extractProtosTaskName = sourceSet.getTaskName('extract', 'proto')
            def extractProtosTask = project.tasks.create(extractProtosTaskName) {
                description = "Extracts proto files/dependencies specified by 'protobuf' configuration"
                actions = [ 
                {
                    project.configurations[protobufConfigName].files.each { file ->
                        if (file.path.endsWith('.proto')) {
                            ant.copy(
                                file: file.path,
                                toDir: project.extractedProtosDir + "/" + sourceSet.getName()
                            )
                            //generateJavaTask.getSource().create(project.files(file))
                        } else if (file.path.endsWith('.jar') || file.path.endsWith('.zip')) {
                            ant.unzip(src: file.path, dest: project.extractedProtosDir + "/" + sourceSet.getName())
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
                                dest: project.extractedProtosDir + "/" + sourceSet.getName(),
                                compression: compression)
                        }
                    }
                } as Action
                ]
            }
            generateJavaTask.dependsOn(extractProtosTask)
            generateJavaTask.getSource().srcDir project.extractedProtosDir + "/" + sourceSet.getName()
            
            sourceSet.java.srcDir getGeneratedSourceDir(project, sourceSet)
            String compileJavaTaskName = sourceSet.getCompileTaskName("java");
            Task compileJavaTask = project.tasks.getByName(compileJavaTaskName);
            compileJavaTask.dependsOn(generateJavaTask)
        }
    }
    
    void configureForSourceSet(Project project, final SourceSet sourceSet, ProtobufCompile compile) {
        def protobufSourceSetName = (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "protobuf" : sourceSet.getName() + "Protobuf")
        def final defaultSource = new DefaultSourceDirectorySet(protobufSourceSetName, "${sourceSet.displayName} protobuf source", project.fileResolver);

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
        compile.conventionMapping.map('plugins') {
            return project.convention.plugins.protobuf.protobufCodeGenPlugins
        }
    }

    private getGeneratedSourceDir(Project project, SourceSet sourceSet) {
        def generatedSourceDir = project.convention.plugins.protobuf.generatedFileDir
				
        return "${generatedSourceDir}/${sourceSet.name}"
    }

}
