import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;

public class MoreAndroidTestHelper {
  public static List<MessageLite> getDefaultInstances() {
    ArrayList<MessageLite> list = new ArrayList<MessageLite>();
    // from src/main/protocolbuffers/more.proto
    list.add(com.example.more.MoreMsg.getDefaultInstance());
    list.add(com.example.more.MoreFoo.getDefaultInstance());
    // from src/androidTest/porotocolbuffers/moresample.proto
    list.add(com.example.more.MoreSampleMsg.getDefaultInstance());
    list.add(com.example.more.MoreSampleFoo.getDefaultInstance());
    return list;
  }
}
