package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver;

class ProtobufPlugin implements Plugin<Project> {
    void apply(final Project project) {
        project.apply plugin: 'java'

        project.convention.plugins.protobuf = new ProtobufConvention();
        def protobufConfig = project.configurations.add('protobuf') {
            visible = false
            transitive = false
            extendsFrom = []
        }
        project.configurations['compile'].extendsFrom = (project.configurations['compile'].extendsFrom + protobufConfig) as Set
        project.sourceSets.allObjects { sourceSet ->
            ProtobufSourceSet protobufSourceSet = new ProtobufSourceSet(sourceSet.displayName, project.fileResolver)
            sourceSet.convention.plugins.put('protobuf', protobufSourceSet)
            protobufSourceSet.protobuf.srcDir("src/${sourceSet.name}/protobuf")
            def generateJavaTaskName = sourceSet.getTaskName('generate', 'proto')
            def compileProtobufTaskName = sourceSet.getCompileTaskName("proto")
            project.logger.info "adding protobuf task named ${compileProtobufTaskName}"
            ProtobufCompile generateJavaTask = project.tasks.add(generateJavaTaskName, ProtobufCompile)
            configureForSourceSet project, sourceSet, generateJavaTask

            Compile compileProtoTask = project.tasks.add(compileProtobufTaskName, Compile)
            compileProtoTask.conventionMapping.map('defaultSource') {
                project.fileTree(dir: "${project.buildDir}/proto-generated/${sourceSet.name}", includes: ['**/*.java'])
            }
            compileProtoTask.conventionMapping.map('classpath') {
                return sourceSet.compileClasspath
            }
            compileProtoTask.conventionMapping.map('destinationDir') {
                return sourceSet.classesDir
            }
            compileProtoTask.dependsOn generateJavaTaskName
            configureProtoc(project, generateJavaTask)
        }

    }

    void configureProtoc(final project, compile) {
        def conventionMapping = compile.getConventionMapping();
        conventionMapping.map("protocPath") {
            return project.convention.plugins.protobuf.protocPath
        }
    }

    void configureForSourceSet(Project project, final SourceSet sourceSet, compile) {
        def conventionMapping = compile.getConventionMapping();
        conventionMapping.map("classpath") {
            return sourceSet.getCompileClasspath()
        }

        def final defaultSource = new DefaultSourceDirectorySet("${sourceSet.displayName} Protobuf source", project.fileResolver);
        defaultSource.include("**/*.proto")
        defaultSource.filter.include("**/*.proto")
        defaultSource.srcDir("src/${sourceSet.name}/proto")
        conventionMapping.map('defaultSource') {
            return defaultSource
        }
        conventionMapping.map('destinationDir') {
            return new File("${project.buildDir}/proto-generated/${sourceSet.name}")
        }
    }
}

class ProtobufSourceSet {
    SourceDirectorySet protobuf
    def ProtobufSourceSet(String displayName, FileResolver fileResolver) {
        protobuf = new DefaultSourceDirectorySet("${displayName} Protobuf source", fileResolver)
    }
}