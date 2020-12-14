import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;

public class MoreUnitTestHelper {
  public static List<MessageLite> getDefaultInstances() {
    ArrayList<MessageLite> list = new ArrayList<MessageLite>();
    // from src/main/protocolbuffers/more.proto
    list.add(com.example.more.MoreMsg.getDefaultInstance());
    list.add(com.example.more.MoreFoo.getDefaultInstance());
    // from src/test/protocolbuffers.moreunittest.proto
    list.add(com.example.more.MoreTestMsg.getDefaultInstance());
    list.add(com.example.more.MoreTestFoo.getDefaultInstance());
    return list;
  }
}
