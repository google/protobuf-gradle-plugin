import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DependentTest {

  @Test public void testProtos() {
    Dependent.WrapperMessage message =
        Dependent.WrapperMessage.newBuilder()
            .setItem(ws.antonov.protobuf.test.Test.Item.getDefaultInstance())
            .setAny(com.google.protobuf.Any.getDefaultInstance())
            .build();
    assertSame(ws.antonov.protobuf.test.Test.Item.getDefaultInstance(),
        message.getItem());
  }
}
