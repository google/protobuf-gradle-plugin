#include <cassert>

#include "Foo.h"
#include "test.pb.h"

#define assertEquals(a, b) assert(a == b)

class FooTest {
  public:
    void testMainProtos() {
      assertEquals(6, Foo::getDefaultInstances().size());
    }

  void testTestProtos() {
    // from src/test/proto/test.proto
    MsgTest::default_instance();
  }
};

int main(int argc, char** argv) {
    ((void)argc);  // Unused
    ((void)argv);  // Unused

    // Tests
    auto fixture = FooTest{};
    fixture.testMainProtos();
    fixture.testTestProtos();

    return 0;
}
