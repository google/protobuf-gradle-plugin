import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class CallJavaClass {
  @org.junit.Test
  fun testCallJavaFoo() {
    assertEquals(12, Foo.getDefaultInstances().size)
  }
}
