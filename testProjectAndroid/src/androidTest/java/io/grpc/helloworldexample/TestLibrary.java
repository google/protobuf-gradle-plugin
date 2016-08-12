package io.grpc.helloworldexample;

public class TestLibrary {
  // From src/test/proto/sample.proto
  com.example.tutorial.Msg msg;

  // From src/main/proto/helloworld.proto
  Helloworld.HelloRequest request;

  // From testProjectLite: src/nano/proto/messages.proto
  io.grpc.testing.Messages.SimpleRequest simpleRequest;

  // From lib/protos.jar
  com.google.protobuf.gradle.test.External.BlobMessage blobMessage;

  TestLibrary() {
  }
}
