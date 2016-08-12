import static org.junit.Assert.assertTrue;

public class LiteTest {
  @org.junit.Test
  public void testLiteProtos() {
    assertTrue(com.google.protobuf.GeneratedMessageLite.class.isAssignableFrom(
            io.grpc.testing.Messages.SimpleRequest.class));
  }
}
