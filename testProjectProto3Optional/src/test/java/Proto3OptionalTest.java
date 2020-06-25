public class Proto3OptionalTest {

  @org.junit.Test
  public void testProto3OptionalField() {
    // from src/test/proto/test.proto
    // this method only exists if --experimental_allow_proto3_optional is used
    Test.MsgTest.getDefaultInstance().hasExplicitVisibility();
  }
}
