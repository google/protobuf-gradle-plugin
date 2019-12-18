import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DependentTest {

  @Test public void testProtos() {
    dependent.Dependent.WrapperMessage message =
        dependent.Dependent.WrapperMessage.newBuilder()
            .setItem(ws.antonov.protobuf.test.Test.Item.getDefaultInstance())
            .setAny(com.google.protobuf.Any.getDefaultInstance())
            .build();
    assertSame(ws.antonov.protobuf.test.Test.Item.getDefaultInstance(),
        message.getItem());
    Dependent2.TestWrapperMessage testMessage =
        Dependent2.TestWrapperMessage.newBuilder()
            .setM(message).build();
  }
}
