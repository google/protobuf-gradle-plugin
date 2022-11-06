package com.google.protobuf.gradle.internal

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.builder.model.SourceProvider
import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.Utils
import com.google.protobuf.gradle.tasks.ProtoSourceSet
import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.SourceTask
import org.gradle.language.jvm.tasks.ProcessResources

@CompileDynamic
class AndroidExt {
  private AndroidExt() {
  }

  static BaseVariant getTestVariant(BaseVariant variant) {
    return variant instanceof TestVariant || variant instanceof UnitTestVariant ? variant.testedVariant : null
  }

  static void configureCompileProtoPathConfAttrs(Configuration conf, BaseVariant variant) {
    AttributeContainer confAttrs = conf.attributes
    variant.compileConfiguration.attributes.keySet().each { Attribute<Object> attr ->
      Object attrValue = variant.compileConfiguration.attributes.getAttribute(attr)
      confAttrs.attribute(attr, attrValue)
    }
  }

  static void addProtoSourcesToAar(BaseVariant variant, ProtoSourceSet protoSourceSet) {
    variant.getProcessJavaResourcesProvider().configure { ProcessResources task ->
      task.from(protoSourceSet.proto) { CopySpec cs ->
        cs.include('**/*.proto')
      }
    }
  }

  static void configureAndroidKotlinCompileTasks(Project project, BaseVariant variant, ProtoSourceSet protoSourceSet) {
    project.plugins.withId("org.jetbrains.kotlin.android") {
      project.afterEvaluate {
        String compileKotlinTaskName = Utils.getKotlinAndroidCompileTaskName(project, variant.name)
        project.tasks.named(compileKotlinTaskName, SourceTask) { SourceTask task ->
          task.source(protoSourceSet.output)
        }
      }
    }
  }

  static void configureProtoPathConfExtendsFrom(
    Project project,
    ProtobufExtension protobufExtension,
    Configuration conf,
    Collection<SourceProvider> sourceSets
  ) {
    sourceSets.each { SourceProvider sourceProvider ->
      ProtoSourceSet protoSourceSet = protobufExtension.sourceSets.getByName(sourceProvider.name)

      String compileOnlyConfName = protoSourceSet.getCompileOnlyConfName()
      Configuration compileOnlyConf = project.configurations.getByName(compileOnlyConfName)
      String implementationConfName = protoSourceSet.getImplementationConfName()
      Configuration implementationConf = project.configurations.getByName(implementationConfName)
      conf.extendsFrom(compileOnlyConf, implementationConf)
    }
  }
}
