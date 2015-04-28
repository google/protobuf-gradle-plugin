package com.google.protobuf.gradle

import groovy.lang.Closure
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.ConfigureUtil

/**
 * A source set convention that defines the properties and methods added to a {@code SourceSet} by
 * the {@link ProtobufPlugin}.
 */
public class ProtobufSourceSet {
  private final SourceDirectorySet proto

  public ProtobufSourceSet(String displayName, FileResolver fileResolver) {
    proto = new DefaultSourceDirectorySet(String.format("%s Proto source", displayName), fileResolver)
    proto.srcDir("src/${displayName}/proto")
    proto.include("**/*.proto")
  }

  public SourceDirectorySet getProto() {
    return proto
  }

  public ProtobufSourceSet proto(Closure configureClosure) {
    ConfigureUtil.configure(configureClosure, getProto())
    return this
  }
}
