#include <memory>
#include <vector>

#include "google/protobuf/message.h"

class Foo {
  public:
    static std::vector<std::unique_ptr<google::protobuf::Message>> getDefaultInstances();
};
