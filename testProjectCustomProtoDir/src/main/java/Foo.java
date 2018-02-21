import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;

public class Foo {
  public static List<MessageLite> getDefaultInstances() {
    ArrayList<MessageLite> list = new ArrayList<MessageLite>();
    // from src/main/protobuf/test.protodevel
    list.add(ws.antonov.protobuf.test.Test.TestMessage.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.AnotherMessage.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.Item.getDefaultInstance());
    list.add(ws.antonov.protobuf.test.Test.DataMap.getDefaultInstance());
    // from src/main/protobuf/sample.proto (java_multiple_files == true, thus no outter class)
    list.add(com.example.tutorial.Msg.getDefaultInstance());
    list.add(com.example.tutorial.SecondMsg.getDefaultInstance());
    // from src/main/protocolbuffers/more.proto
    list.add(More.MoreMsg.getDefaultInstance());
    list.add(More.Foo.getDefaultInstance());
    // from "src/main/protocol buffers/spaceinpath.proto"
    list.add(Spaceinpath.SpaceInPath.getDefaultInstance());
    return list;
  }
}
