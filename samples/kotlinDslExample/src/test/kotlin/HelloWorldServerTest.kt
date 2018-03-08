import org.junit.Assert.assertEquals

import HelloWorldServer.GreeterImpl
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.testing.GrpcServerRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [HelloWorldServer].
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 *
 *
 * For more unit test examples see [io.grpc.examples.routeguide.RouteGuideClientTest] and
 * [io.grpc.examples.routeguide.RouteGuideServerTest].
 */
@RunWith(JUnit4::class)
class HelloWorldServerTest {
    /**
     * This creates and starts an in-process server, and creates a client with an in-process channel.
     * When the test is done, it also shuts down the in-process client and server.
     */
    @get:Rule
    val grpcServerRule = GrpcServerRule().directExecutor()

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    @Throws(Exception::class)
    fun greeterImpl_replyMessage() {
        // Add the service to the in-process server.
        grpcServerRule.getServiceRegistry().addService(GreeterImpl())

        val blockingStub = GreeterGrpc.newBlockingStub(grpcServerRule.getChannel())
        val testName = "test name"

        val reply = blockingStub.sayHello(HelloRequest.newBuilder().setName(testName).build())

        assertEquals("Hello " + testName, reply.message)
    }
}