package io.grpc.helloworldexample;

import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UnitTestKotlin {
  // From src/main:
  val activity = HelloWorldActivityKotlin()
  // From src/main/proto/helloworld.proto
  var request = Helloworld.HelloRequest.getDefaultInstance()
  var response = Helloworld.HelloReply.getDefaultInstance()
  // From lib/protos.jar
  var blob = com.google.protobuf.gradle.test.External.BlobMessage.getDefaultInstance()
  // From test proto:
  var msg1 = com.example.tutorial.UnitTestMsg.getDefaultInstance()
  var msg2 = com.example.tutorial.UnitTestSecondMsg.getDefaultInstance()

  @Test
  fun ensureAndroidTestProtosNotVisible() {
    // we should not see the protos from src/test/proto/
    try {
      Class.forName("com.example.tutorial.Msg");
      fail();
    } catch (expected: ClassNotFoundException) {
      // noop
    }
  }
}
