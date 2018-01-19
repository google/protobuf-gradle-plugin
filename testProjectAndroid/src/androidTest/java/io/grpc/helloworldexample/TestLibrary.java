package io.grpc.helloworldexample;

public class TestLibrary {
  HelloworldActivity activity;

  // From src/androidTest/proto/sample.proto
  com.example.tutorial.Msg msg;

  // From src/main/proto/helloworld.proto
  Helloworld.HelloRequest request;

  // From testProjectLite: src/nano/proto/messages.proto
  io.grpc.testing.Messages.SimpleRequest simpleRequest;

  // From lib/protos.jar
  com.google.protobuf.gradle.test.External.BlobMessage blobMessage;

  // TODO(zpencer): reflectively check that unit test protos are not visible
  // This requires figuring out how to get androidTest to run. Currently the sources in androidTest
  // are compiled, but test classes are not actually executed.

  TestLibrary() {
  }
}
