package io.grpc.helloaarexample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.grpc.helloworldexample.R

class HelloAarActivityKotlin : AppCompatActivity() {
  // From testProjectAndroidLibrary: src/main/proto/helloAar.proto
  var request = HelloAar.HelloRequest.getDefaultInstance()

  // From testProjectLite: src/nano/proto/messages.proto
  var simpleRequest = io.grpc.testing.Messages.SimpleRequest.getDefaultInstance()

  // From lib/protos.jar
  var blobMessage = com.google.protobuf.gradle.test.External.BlobMessage.getDefaultInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_helloworld)
  }
}
