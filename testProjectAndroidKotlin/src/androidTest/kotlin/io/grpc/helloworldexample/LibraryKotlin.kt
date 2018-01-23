package io.grpc.helloworldexample

class LibraryKotlin {
  // From src/main/proto/helloworld.proto
  var request = Helloworld.HelloRequest.getDefaultInstance()
  var response = Helloworld.HelloReply.getDefaultInstance()
  // From lib/protos.jar
  var blob = com.google.protobuf.gradle.test.External.BlobMessage.getDefaultInstance()
  // From androidTest proto:
  var msg1 = com.example.tutorial.Msg.getDefaultInstance()
  var msg2 = com.example.tutorial.SecondMsg.getDefaultInstance()

  // TODO(zpencer): reflectively check that unit test protos are not visible
  // This requires figuring out how to get androidTest to run. Currently the sources in androidTest
  // are compiled, but test classes are not actually executed.
}
