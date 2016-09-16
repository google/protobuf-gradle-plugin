using Google.Protobuf.Msg;

namespace Sample.src.main.csharp
{
    class ReferenceProtobuf
    {
        public ReferenceProtobuf()
        {
            Msg msg = new Msg
            {
                Foo = "Bar"
            };
        }
    }
}
