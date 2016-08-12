import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FooTest {
  @org.junit.Test
  public void testMainProtos() {
    assertEquals(11, Foo.getDefaultInstances().size());
  }

  @org.junit.Test
  public void testTestProtos() {
    // from src/test/proto/test.proto
    Test.MsgTest.getDefaultInstance();
    // from lib/protos-test.tar.gz
    test.Stuff.BlahTest.getDefaultInstance();
  }

  @org.junit.Test
  public void testGrpc() {
    // from src/grpc/proto/
    assertTrue(com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(
        io.grpc.testing.integration.Messages.SimpleRequest.class));
    assertTrue(Object.class.isAssignableFrom(io.grpc.testing.integration.TestServiceGrpc.class));
  }
}
