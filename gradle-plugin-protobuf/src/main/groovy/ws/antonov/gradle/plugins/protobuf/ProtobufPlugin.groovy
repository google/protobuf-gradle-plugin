package ws.antonov.gradle.plugins.protobuf

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.tasks.ConventionValue
import org.gradle.api.plugins.Convention
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.file.DefaultSourceDirectorySet

class ProtobufPlugin implements Plugin<Project> {
    void apply(final Project project) {
        //println "*** --- Initializing ProtobufPlugin --- ***"
        project.apply plugin: 'base' // We apply the base plugin to have the clean<taskname> rule
        project.apply plugin: 'java'

        project.convention.plugins.protobuf = new ProtobufConvention();

        project.convention.plugins.java.sourceSets.each {
            def srcSetName = it.name
            it.java.srcDir("${project.buildDir}/proto-generated/${srcSetName}")
            //println "${project.buildDir}/proto-generated/${srcSetName}"

            def compileProtobufTaskName = it.getCompileTaskName("proto")
            //println compileProtobufTaskName
            
            ProtobufCompile compileProtobufTask = project.tasks.add(compileProtobufTaskName, ProtobufCompile.class);

            String compileJavaTaskName = it.getCompileTaskName("java");
            //println compileJavaTaskName
            
            Task compileJavaTask = project.tasks.getByName(compileJavaTaskName);
            compileJavaTask.dependsOn(compileProtobufTask)
            compileProtobufTask.description = "Compiles the ${srcSetName} Protobuf source."

            configureProtoc(project, compileProtobufTask)
            configureForSourceSet(project, it, compileProtobufTask)
        }
        //println "*** --- Done Initializing ProtobufPlugin --- ***\n\n"
    }

    void configureProtoc(final project, compile) {
        def conventionMapping = compile.getConventionMapping();
        conventionMapping.map("protocPath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return project.convention.plugins.protobuf.protocPath
            }
        });
    }

    void configureForSourceSet(final project, final sourceSet, compile) {
        def conventionMapping = compile.getConventionMapping();
        conventionMapping.map("classpath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getCompileClasspath();
            }
        });

        def final defaultSource = new DefaultSourceDirectorySet("${sourceSet.displayName} Protobuf source", project.fileResolver);
        defaultSource.include("**/*.proto")
        defaultSource.filter.include("**/*.proto")
        defaultSource.srcDir("src/${sourceSet.name}/proto")
        conventionMapping.map("defaultSource", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return defaultSource
            }
        });
        conventionMapping.map("destinationDir", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return new File("${project.buildDir}/proto-generated/${sourceSet.name}")
            }
        });
    }
}