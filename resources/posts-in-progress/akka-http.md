I've been a huge fan of both Akka and Spray for a long time, so naturally I was very delighted when I found out that teams behind both were joining forces to deliver an http module for Akka which would become the successor for Spray. For the uninitiated, Akka is JVM based framework written in scala that delivers an actor based concurrency platforms that provides all the tools to distribute programs both across all the cores on an individual machine and all the machines in a networked cluster. Spray meanwhile is a framework built on top of Akka that provides a light-weight http client and server implementation that is asynchronous and highly efficient.

Akka-Http represents the combination of these two software packages to provide a high performance http implementation built directly into Akka itself. Additionally, one of the more exciting things about this new module is the fact that it is being implemented from the ground up around reactive streams. The Reactive Streams specification is being developed as a joint effort between many different individuals and companies to provide a standard for asynchronous stream processing and non blocking back pressure on the JVM. For more information on why this is a good thing check out the [Reactive Streams Initiative](http://www.reactive-streams.org/), it does a much better job explaining these ideas than I could do here in this blog post.

Being that akka-http is being built from the ground up to support the reactive streams specification, an http connection can simply be viewed as a stream of http requests resulting in http responses. What's even more exciting is the fact that an individual request or response (especially chunked ones) can be viewed itself as one of these streams as well. If both systems on either side of the network boundary treat the interaction as a reactive stream then the back pressure propogation works appropriately in this networked context. This provides a special type of synergy among these systems that allow upstream systems to respond to the backpressure being generated from downstream systems that they may or may not be in direct contact with.

If you ask me this is very exciting stuff! However before I get into the meat of this blog post, I would like to point out that akka-http and akka-stream (Akka's reactive stream implemenation) are under heavy development and as such the api will most likely change over time. Let's start off by taking a closer look at what we mean by reactive streams.



### **Flow DSL**

Akka provides a flow dsl to greatly simplify creating and transforming streams that mostly removes the need to create actors to represent every step in the process. Simply put a ```Flow``` allows us to define a stream source and a provides a number of stream transformation helpers that allows us to define functions that manipulate the stream. Once a flow is defined we can use a configurable ```FlowMaterializer``` to create the underlying ```StreamProcessor```'s used to process the stream. We're not going to look this comprehensively here because this is the part most likely to change in the near future, but we'll walk through a few examples to get a feel for how this works.

First let's look at creating a flow. There are a number of different types of sources we can use to 'feed' data into a flow:

1. We can create a flow from a ```Publisher[T]``` that will subscribe to that publisher and receive values from it as they are emitted.

    ```scala
    val publisherFlow = Flow(publisher)
    ```

2. We can create a flow by running a closure on a periodic basis to produce elements. This one will produce a random value every second.

    ```scala
    val periodicRandomFlow = Flow(1.second, 1.second, {() => Random.nextInt})
    ```

3. We can create a flow from an iterable that will use the elements in the iterable as a source for the stream.

    ```scala
    val iterableFlow = Flow(Seq(1,2,3,4,5,6))
    ```

4. We can create a flow from a future will result in a stream of a single element realized when the future completes

    ```scala
    val futureFlow = Flow(Future { Thread.sleep(5000); 1 })
    ```

These are only a few of the ways that flows can be created....this list will likely grow as akka-stream matures. Now let's take a look at how this dsl allows us to easily manipulate streams. Flow contains most of the functional collections processing methods that we've all come to know and love. This functions can be used in a very familiar manner:

```scala
val transformedFlow = periodicRandomFlow.map(_ * 2).filter(_ % 3 == 0).drop(50).take(100)
val printFlow = transformedFlow.foreach(println)
```

Here we've created a new flow that has been transformed in a number of different ways using common collections processing methods. Additionally each transformation creates a new flow, so we can reuse this transformed flow and create a new one that prints every element in the stream.

Now that we've seen how to create and transform flows, let's take a look at how to run them. To do this we'll need a ```FlowMaterializer``` which is an entity that knows how to take a ```Flow``` and turn it into concrete Processor instances which actually run the stream. To create a ```FlowMaterializer``` we need to create and use a ```MaterializerSettings``` object and we need an ```ActorRefFactory``` which is used to create the actors used in the stream. The ```MaterializerSettings``` object exists primarily to allow us to control buffer sizes used by the processors and to provide the execution context used to run each of the processors. Let's create a ```FlowMaterializer```:

```scala
implicit val system = ActorSystem("test-system")
val settings = MaterializerSettings(system)
implicit val materializer = FlowMaterializer(settings)
``` 

Now that we have an implicit materializer we can "materialize" the flow in a number of different ways:

```scala
// Prints each element from the transformed flow and throws away the resulting elements (which would be Unit)
printFlow.consume()
// Returns a Publisher[Int] that can be consumed by another Flow, a Subscriber, or a Processor
transformedFlow.toPublisher()
// Returns a Future[Int] representing the first element in the stream
tranformedFlow.toFuture()
// Prints each element from the transformed flow executes callback that takes an argument of Try[Unit]
printlnFlow.onComplete {
    case Success(_) => println("Completed Successfully")
    case Failure(err) => println("Failed with exception", err)
}
```

## **Akka-Http**