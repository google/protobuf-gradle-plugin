import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;

public class MoreHelper {
  public static List<MessageLite> getDefaultInstances() {
    ArrayList<MessageLite> list = new ArrayList<MessageLite>();
    // from src/main/protobuf/more.proto
    list.add(com.example.more.MoreMsg.getDefaultInstance());
    list.add(com.example.more.MoreFoo.getDefaultInstance());
    return list;
  }
}
