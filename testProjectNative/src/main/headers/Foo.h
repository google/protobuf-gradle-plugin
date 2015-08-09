#include <memory>
#include <vector>

#include "google/protobuf/message_lite.h"

class Foo {
  public:
    static std::vector<std::unique_ptr<google::protobuf::MessageLite>> getDefaultInstances();
};
