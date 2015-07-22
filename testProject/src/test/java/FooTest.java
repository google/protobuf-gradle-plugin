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
  public void testNanoMessages() {
    // from src/nano/proto/message.proto with 'java_multiple_files=false'
    assertTrue(com.google.protobuf.nano.MessageNano.class.isAssignableFrom(
        nano.Messages.SimpleRequest.class));
  }

  @org.junit.Test
  public void testGrpc() {
    // from src/grpc/proto/
    assertTrue(com.google.protobuf.GeneratedMessage.class.isAssignableFrom(
        io.grpc.testing.integration.Messages.SimpleRequest.class));
    assertTrue(Object.class.isAssignableFrom(io.grpc.testing.integration.TestServiceGrpc.class));
  }

  @org.junit.Test
  public void testGrpcNano() {
    // from src/grpc_nano/proto/
    assertTrue(com.google.protobuf.nano.MessageNano.class.isAssignableFrom(
        io.grpc.testing.integration.nano.Messages.SimpleRequest.class));
    assertTrue(Object.class.isAssignableFrom(
        io.grpc.testing.integration.nano.TestServiceGrpc.class));
  }
}
