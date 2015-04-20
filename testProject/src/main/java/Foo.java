import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;

public class Foo {
  public static List<MessageLite> getDefaultInstances() {
    ArrayList<MessageLite> list = new ArrayList<MessageLite>();
    // from src/main/proto/test.proto
    list.add(ws.antonov.protobuf.test.Test.TestMessage.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.AnotherMessage.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.Item.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.DataMap.getDefaultInstance());
    // from src/main/proto/sample.proto (java_multiple_files == true, thus no outter class)
    list.add(com.example.tutorial.Msg.getDefaultInstance());
    list.add(com.example.tutorial.SecondMsg.getDefaultInstance());
    // from lib/protos.tar.gz/stuff.proto
    list.add(Stuff.Blah.getDefaultInstance());
    // from ext/more.proto
    list.add(More.MoreMsg.getDefaultInstance());
    list.add(More.Foo.getDefaultInstance());
    // from ext/test1.proto
    list.add(Test1.Test1Msg.getDefaultInstance());
    // from ext/test2.proto
    list.add(Test2.Test2Msg.getDefaultInstance());
    return list;
  }
}
