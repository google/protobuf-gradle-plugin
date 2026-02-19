package io.grpc.helloworldexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class HelloWorldActivityKotlin : AppCompatActivity() {
  // From src/main/proto/helloworld.proto
  var request = Helloworld.HelloRequest.getDefaultInstance()

  // From testProjectLite: src/nano/proto/messages.proto
  var simpleRequest = io.grpc.testing.Messages.SimpleRequest.getDefaultInstance()

  // From lib/protos.jar
  var blobMessage = com.google.protobuf.gradle.test.External.BlobMessage.getDefaultInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_helloworld)
  }
}
