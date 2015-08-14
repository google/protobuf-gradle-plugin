#include <cassert>

#include "Foo.h"

int main(int argc, char** argv) {
    ((void)argc);  // Unused
    ((void)argv);  // Unused

    // Tests
    assert(6 == Foo::getDefaultInstances().size());

    return 0;
}
