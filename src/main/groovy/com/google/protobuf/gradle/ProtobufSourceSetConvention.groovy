package com.google.protobuf.gradle

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.ConfigureUtil

/**
 * A source set convention that defines the properties and methods added to a {@code SourceSet} by
 * the {@link ProtobufPlugin}.
 */
public class ProtobufSourceSetConvention {
  private final SourceDirectorySet proto

  public ProtobufSourceSetConvention(Project project, String name, FileResolver fileResolver) {
    proto = new ProtobufSourceDirectorySet(project, name, fileResolver)
    proto.srcDir("src/${name}/proto")
    proto.include("**/*.proto")
    if (project.hasProperty('android')) {
      project.android.sourceSets.maybeCreate(name).java.srcDir("build/generated-sources/${name}")
    }
  }

  public ProtobufSourceDirectorySet getProto() {
    return proto
  }

  public ProtobufSourceSetConvention proto(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, getProto())
    return this
  }
}
