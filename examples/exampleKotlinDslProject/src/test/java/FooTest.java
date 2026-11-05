import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FooTest {
  @org.junit.jupiter.api.Test
  public void testMainProtos() {
    assertEquals(11, Foo.getDefaultInstances().size());
  }

  @org.junit.jupiter.api.Test
  public void testTestProtos() {
    // from src/test/proto/test.proto
    Test.MsgTest.getDefaultInstance();
    // from lib/protos-test.tar.gz
    test.Stuff.BlahTest.getDefaultInstance();
  }

  @org.junit.jupiter.api.Test
  public void testGrpc() {
    // from src/grpc/proto/
    assertTrue(com.google.protobuf.GeneratedMessage.class.isAssignableFrom(
        io.grpc.testing.integration.Messages.SimpleRequest.class));
    assertTrue(Object.class.isAssignableFrom(io.grpc.testing.integration.TestServiceGrpc.class));
  }
}
