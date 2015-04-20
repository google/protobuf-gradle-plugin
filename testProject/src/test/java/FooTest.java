
public class FooTest {
  @org.junit.Test
  public void testMainProtos() {
    org.junit.Assert.assertEquals(11, Foo.getDefaultInstances().size());
  }

  @org.junit.Test
  public void testTestProtos() {
    // from src/test/proto/test.proto
    Test.MsgTest.getDefaultInstance();
    // from lib/protos-test.tar.gz
    test.Stuff.BlahTest.getDefaultInstance();
  }
}
