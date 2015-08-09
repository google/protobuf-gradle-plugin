#include "Foo.h"

#include "ws/antonov/protobuf/test/test.pb.h"
#include "com/example/tutorial/sample.pb.h"

using ::google::protobuf::MessageLite;
using namespace ws::antonov::protobuf::test;
//using namespace com::example::tutorial;

std::vector<std::unique_ptr<MessageLite>> Foo::getDefaultInstances() {
    auto list = std::vector<std::unique_ptr<MessageLite>>{};
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
// public class Foo {
//   public static List<MessageLite> getDefaultInstances() {
//     ArrayList<MessageLite> list = new ArrayList<MessageLite>();
//     // from src/main/proto/test.proto
//     list.add(ws.antonov.protobuf.test.Test.TestMessage.getDefaultInstance());
//     list.add(ws.antonov.protobuf.test.Test.AnotherMessage.getDefaultInstance());
//     list.add(ws.antonov.protobuf.test.Test.Item.getDefaultInstance());
//     list.add(ws.antonov.protobuf.test.Test.DataMap.getDefaultInstance());
//     // from src/main/proto/sample.proto (java_multiple_files == true, thus no outter class)
//     list.add(com.example.tutorial.Msg.getDefaultInstance());
//     list.add(com.example.tutorial.SecondMsg.getDefaultInstance());
//     // from lib/protos.tar.gz/stuff.proto
//     list.add(Stuff.Blah.getDefaultInstance());
//     // from ext/more.proto
//     list.add(More.MoreMsg.getDefaultInstance());
//     list.add(More.Foo.getDefaultInstance());
//     // from ext/test1.proto
//     list.add(Test1.Test1Msg.getDefaultInstance());
//     // from ext/test2.proto
//     list.add(Test2.Test2Msg.getDefaultInstance());
//     return list;
//   }
// }