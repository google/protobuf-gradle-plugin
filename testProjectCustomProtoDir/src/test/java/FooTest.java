
public class FooTest {
  @org.junit.Test
  public void testMainProtos() {
    org.junit.Assert.assertEquals(10, Foo.getDefaultInstances().size());
  }

  @org.junit.Test
  public void testTestProtos() {
    // from src/test/protocolbuffers/test.proto
    Test.MsgTest.getDefaultInstance();
  }
}
