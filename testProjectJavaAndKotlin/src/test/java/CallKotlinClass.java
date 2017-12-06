import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CallKotlinClass {
  @org.junit.Test
  public void testMainProtosKotlin() {
    // call kotlin class from java
    assertEquals(11, new KotlinFoo().getDefaultInstances().size());
  }
}
