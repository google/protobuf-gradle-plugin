package io.grpc.helloworldexample;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UnitTest {
  private final HelloworldActivity activity = new HelloworldActivity();
  // From src/test/proto/unittest.proto
  private com.example.tutorial.UnitTestMsg msg;

  // From src/main/proto/helloworld.proto
  private Helloworld.HelloRequest request;

  // From testProjectLite: src/nano/proto/messages.proto
  private io.grpc.testing.Messages.SimpleRequest simpleRequest;

  // From lib/protos.jar
  private com.google.protobuf.gradle.test.External.BlobMessage blobMessage;

  @Test
  public void ensureAndroidTestProtosNotVisible() throws Exception {
    // we should not see the protos from src/androidTest/proto/
    try {
      Class<?> ignored = Class.forName("com.example.tutorial.Msg");
      fail();
    } catch (ClassNotFoundException expected){
      // noop
    }
  }
}
