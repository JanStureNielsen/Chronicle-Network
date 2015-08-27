High Performance Network library
===

# Purpose
The Netty library is a benchmark for high performance libraries. 
    Netty is designed for scalability in particular, horizontal scalability is
    based on the Selector and it has highly tuned this facility.
   
This library is designed to be lower latency and support higher throughputs 
    by employing techniques used in low latency trading systems.
    
# Transports
Network current support TCP only. 
 
Planned support for
* Shared Memory
* Unreliable UDP

# Support
This library will require Java 8
   
# Simplicity
The library is a cut down version of the functionality Netty provides, 
    so it needs to be simpler to reflect this.
   
# Testing
The target environment is to support TCP over 10 Gig-E ethernet.  In prototype
    testing, this library has half the latency and support 30% more bandwidth.
    
A key test is that it shouldn't GC more than once (to allow for warm up) with -mx64m

# Downsides
This comes at the cost of scalability for large number os connections.
     In this situation, this library should perform at least as well as netty.

# Comparisons

## Netty
Netty has a much wider range of functionality, however it creates some 
   garbage in it's operation (less than using plain NIO Selectors) and isn't 
   designed to support busy waiting which gives up a small but significant delay.


# Example
## Creating a simple serer and client


The full source code of this example can be found at

```java
net.openhft.performance.tests.network.SimpleServerAndClientTest.test
```

Below are the key part of this code explained in a bit more detail.

#### TCPRegistry

The TCPRegistry is most useful for unit test, it allows you to either provide a true host and port for example "localhost:8080" or if you would
rather let the application allocate you a free port at random, you can just provide a text reference to the port,
for example "host.port", you can provide any text you want, it will always be taken as a reference,
that is unless its correctly formed like "<hostname>:<port>”, then it will use the exact host and port you provide.
The reason we offer this functionality is quiet often in unit tests you wish to start a test via loopback,
followed often by another test via loopback, if the first test does not shut down correctly it can impact on the
second test. Giving each test a unique port is one solution, but then managing those ports can become a problem
in its self. So we created the TCPRegistry which manages those ports for you, when you come to clean up at the end
of each test, all you have to do it call TCPRegistry.reset() and it will ensure that any remaining ports that
are open will be closed.

```java
// this the name of a reference to the host name and port,
// allocated automatically when to a free port on localhost
final String desc = "host.port";
TCPRegistry.createServerSocketChannelFor(desc);

// we use an event loop rather than lots of threads
EventGroup eg = new EventGroup(true);
eg.start();
```

#### Create and Start the Server

This is an example of how to create and start a simple server, this server is configured with TextWire, so
the client must also be configured with TextWire. The port that we will use will be ( in this example ) determined
by the TCP Registry, of course in a real life production environment you may decide not to use the
TcpRegistry or if you still use the TcpRegistry you can use a fixed <host>:<port>

```java
final String expectedMessage = "<my message>";
AcceptorEventHandler eah = new AcceptorEventHandler(desc,
    () -> new WireEchoRequestHandler(WireType.TEXT), VanillaSessionDetails::new, 0, 0);
eg.addHandler(eah);
final SocketChannel sc = TCPRegistry.createSocketChannel(desc);
sc.configureBlocking(false);
```

#### Server Message Processing

An example of how to process a message on the server, in this simple example we
receive and update and then immediately send back a response, however there are
other solutions that can be implemented using Chronicle-Network, such as the server
responding later to a client subscription.


```java
/**
 * This code is used to read the tid and payload from a wire message,
 * and send the same tid and message back to the client
 */
public class WireEchoRequestHandler extends WireTcpHandler {

    public WireEchoRequestHandler(@NotNull Function<Bytes, Wire> bytesToWire) {
        super(bytesToWire);
    }


    /**
     * simply reads the csp,tid and payload and sends back the tid and payload
     *
     * @param inWire  the wire from the client
     * @param outWire the wire to be sent back to the server
     * @param sd      details about this session
     */
    @Override
    protected void process(@NotNull WireIn inWire,
                           @NotNull WireOut outWire,
                           @NotNull SessionDetailsProvider sd) {

        inWire.readDocument(m -> {
            outWire.writeDocument(true, meta -> meta.write(() -> "tid")
                    .int64(inWire.read(() -> "tid").int64()));
        }, d -> {
            outWire.writeDocument(false, data -> data.write(() -> "payloadResponse")
                    .text(inWire.read(() -> "payload").text()));
        });
    }
}
```


#### Create and start the Client

This code below create the TcpChannelHub, the TcpChannelHub is used by your client code to send messages to the server.
each message that you send the server must have a unique transaction id ( we call this the tid ), when the server responds to
the client, its important that the server send back the tid, as the TcpChannelHub will look at each message
that sent from the server and marshall that message onto your appropriate client thread.


```java
TcpChannelHub tcpChannelHub = TcpChannelHub(null, eg, WireType.TEXT, "", SocketAddressSupplier.uri(desc), false);
```

given that we are not providing fail-over support in this example the simple SocketAddressSupplier.uri(desc), is used.


#### Create the message the client sends to the server
```java
// the tid must be unique, its reflected back by the server, it must be at the start
// of each message sent from the server to the client. Its use by the client to identify which
// thread will handle this message
final long tid = tcpChannelHub.nextUniqueTransaction(System.currentTimeMillis());

// we will use a text wire backed by a elasticByteBuffer
final Wire wire = new TextWire(Bytes.elasticByteBuffer());

wire.writeDocument(true, w -> w.write(() -> "tid").int64(tid));
wire.writeDocument(false, w -> w.write(() -> "payload").text(expectedMessage));
```

#### write the data to the socket
```java
tcpChannelHub.lock(() -> tcpChannelHub.writeSocket(wire));
```

#### read the reply from the socket
```java
// read the reply from the socket ( timeout after 1 second ), note: we have to pass the tid
Wire reply = tcpChannelHub.proxyReply(TimeUnit.SECONDS.toMillis(1), tid);
```

####  check the result of the reply check the result
```java
// read the reply and check the result
reply.readDocument(null, data -> {
    final String text = data.read(() -> "payloadResponse").text();
    Assert.assertEquals(expectedMessage, text);
});
```


#### shutdown and cleanup
```java
eg.stop();
TcpChannelHub.closeAllHubs();
TCPRegistry.reset();
tcpChannelHub.close();
```


