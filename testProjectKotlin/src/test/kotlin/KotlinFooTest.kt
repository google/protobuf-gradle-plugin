import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class KotlinFooTest {
  @org.junit.Test
  fun testMainProtos() {
    assertEquals(11, KotlinFoo().getDefaultInstances().size)
  }

  @org.junit.Test
  fun testTestProtos() {
    // from src/test/proto/test.proto
    Test.MsgTest.getDefaultInstance()
    // from lib/protos-test.tar.gz
    test.Stuff.BlahTest.getDefaultInstance()
  }

  @org.junit.Test
  fun testGrpc() {
    // This is a quick and dirty port from java, and is likely not idiomatic kotlin:

    // from src/grpc/proto/
    assertTrue(
        com.google.protobuf.GeneratedMessageV3::class.java.isAssignableFrom(
            io.grpc.testing.integration.Messages.SimpleRequest::class.java))
    assertTrue(Object::class.java.isAssignableFrom(io.grpc.testing.integration.TestServiceGrpc::class.java))
  }
}
