#include <memory>
#include <vector>

#include "Foo.h"

#include "ws/antonov/protobuf/test/test.pb.h"
#include "com/example/tutorial/sample.pb.h"

using namespace ws::antonov::protobuf::test;
using ::google::protobuf::Message;

std::vector<std::unique_ptr<Message>> Foo::getDefaultInstances() {
    auto list = std::vector<std::unique_ptr<Message>>{};
    // from src/main/proto/test.proto
    list.push_back(std::unique_ptr<TestMessage>{new TestMessage{}});
    list.push_back(std::unique_ptr<AnotherMessage>{new AnotherMessage{}});
    list.push_back(std::unique_ptr<Item>{new Item{}});
    list.push_back(std::unique_ptr<DataMap>{new DataMap{}});
    // from src/main/proto/sample.proto
    list.push_back(std::unique_ptr<Msg>{new Msg{}});
    list.push_back(std::unique_ptr<SecondMsg>{new SecondMsg{}});
    return list;
}
