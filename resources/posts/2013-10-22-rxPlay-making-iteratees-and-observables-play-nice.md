---
title : RxPlay: Diving Into Iteratees and Observables and Making Them Place Nice
tags : [scala, rxJava, play2, iteratees]
---

Reactive programming has been picking up in a big way as of late with [good reason](http://www.reactivemanifesto.org/). In particular there has been a big push in the Scala ecosystem to structure applications so that they are fully reactive and do not block unnecessarily. Two fantastic tools that help faciliate this type of programming are Play Iteratees and RxJava observables. Iteratees are built into the Play 2 Framework and was recently made available as a standalone import and RxJava is a implementation of .net reactive extensions created by the guys at Netflix. I plan on touching on the basics of how these tools work, the similarities between them, and how they can be used seamlessly together.


## Play Iteratees

First up are play iteratees. Iteratees allow us to progressively transform and consume streams of data in a reactive manner. However before we get into the details, let's first set up our project.

### Setting Up

To follow along and evaluate the following examples, the play iteratees library can be included in any sbt project by adding the following dependency:

```scala
  "play" %% "play-iteratees" % "2.1.5"
```

and including the following imports (Only the first import is required to work with iteratees, others are for example purposes):

```scala
  import play.api.libs.iteratee._
  import scala.concurrent._
  import scala.concurrent.duration._
```


### Enumerators

You thought we were talking about iteratees didn't you? Well actually we are, there are three main concepts that need to be understood when working with play iteratees, namely enumerators, enumeratees, and iteratees. We'll cover enumeratees and iteratees later.

An enumerator is a data producer. This data can be coming from an array, a collection, or a stream of bytes from disk. What makes an enumerator interesting is that the data can be available now or asynchronously at some point in the future:

```scala
  val intEnumerator: Enumerator[Int] = Enumerator(1,2,3,4)
  val fileEnumerator: Enumerator[Array[Byte]] = Enumerator.fromFile(new File("test.txt"), 1)
```

The Enumerator object has a helpful apply method that allows us to create an enumerator from any number of elements, here we've created an enumerator that represents the elements 1, 2, 3, and 4. The second enumerator we've created represents the bytes in a file with each data chunk being a buffer of only size 1 meaning that it will only produce all the bytes in the file 1 byte at a time.

Let's look at a more interesting asynchronous example where we have our enumerator asynchronously generate timestamps for us:

```scala
  val asyncEnumerator: Enumerator[Long] = Enumerator.generateM (
    future { Thread.sleep(50); Some(System.currentTimeMillis) }
  )
```

In this example we are creating an enumerator that will asynchrounously produce a timestamp every 500 milliseconds. To create this enumerator we use the generateM function of the Enumerator object which takes a by reference argument that produces a future with an optional value inside. It will then "produce" the value contained in the future once it completes unless it's value is ```None``` in which case it will signal completion of the data stream. Note that we're using Thread.sleep to introduce the delay in producing timestamps, never ever do this in practice, this is just useful for illustrative purposes (Use something like the akka scheduler instead).

This is a good time to mention how an enumerator signals to it's consumer what state it's currently in. An enumerator can produce 3 different values, ```Input[E]```, ```Input.EOF```, and ```Input.Empty```. ```Input[E]``` represents a new value in the data stream, ```Input.EOF``` signifies that the end of the stream has been reached, and ```Input.Empty``` signifies that the data source is empty.

On their own enumerators don't seem too terribly interesting, however when combined with an iteratees their real power starts to show through.

### Iteratees

Iteratees are objects that can be used to iterate over the data in an enumerator in a generic fashion. They do this by processing one chunk of input at a time, building a context, and then emit a single (future) value upon completion of the enumerator. It helps (at least for me) to think of an iteratee as a means to "fold" over the data created by the enumerator.

Here are a few examples:

```scala
  val sumIteratee = Iteratee.fold(0) { _ + _}
  val printIteratee = Iteratee.foreach(println(_))
```

There are many helper functions that allow you to easily create iteratees that correspond to normal collection processing functions. The sum and print iteratees should be fairly straight forward. These helper functions handle the state machine / lifecycle aspect (Input,EOF,Empty) of creating an iteratee for you. However you could explicitly create an iteratee that manually handles the enumerator states as they are produced. I won't be covering the internals of Iteratees here specifically how it operates as a state machine, but you can find a fantastic [tutorial on them here](http://mandubian.com/2012/08/27/understanding-play2-iteratees-for-normal-humans/).

Note that iteratees are immutable and as such a single iteratee can be applied to multiple enumerators. Iteratees are applied to enumerators as follows:

```scala
  intEnumerator.run(Iteratee.fold(0) { _ + _}) 
      // => Returns Future[Int] representing sum
  asyncEnumerator.run(Iteratee.foreach(println(_))) 
      // => Returns Future[Unit] and prints successive timestamps every 50 ms.
```

Using the run function on the enumerators we can inject the iteratee into the enumerator and use it iterate over each value the enumerator produces. The first one retains an accumulator that is incrementally added to each produced value which results in the total sum when the enumerator produces the Input.EOF signal. Notice that the second iterator simply prints each element as it is produced and returns a Future of type Unit.

### Enumeratees

Enumeratees are a means of transforming an enumerator in some way. There are many helper functions to create enumeratees provided on the [Enumeratee Object](http://www.playframework.com/documentation/2.0/api/scala/index.html#play.api.libs.iteratee.Enumeratee$). These are common map, filter, take, etc. type operations:

```scala
  val filterOdd: Enumeratee[Int,Int] = Enumeratee.filter[Int](_ % 2 != 0)
  val takeFive: Enumeratee[Int,Int] = Enumeratee.take[Int](5)
  val intToString: Enumeratee[Int,String] = Enumeratee.map(_.toString)
  val composed: Enumeratee[Int,String] = filterOdd compose takeFive compose intToString
   Enumerator(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15) through composed run Iteratee.foreach(println(_))
    // => Prints: "1", "3", "5", "7", "9"
```

Enumeratees are the simplest of the three concepts, but are necessary to chain together and apply various data transformations to an enumerator.

This pretty much wraps up the basics of Iteratees. At the very least we have a high level understanding how iteratees work and where they could be applied. Now we'll take a step back and talk a little bit about RxJava.


## RxJava

RxJava is a JVM based implementation of Microsoft Reactive Extensions with language bindings for Java, Scala, Clojure, and Groovy. It exposes an Observable abstraction which is meant as an asynchronous counterpart to Iterable. It allows us to react asynchronously to a stream of events (sound familiar?). 

Before we dive into code let's set up our project to work with RxJava.

### Setting Up

Add the sbt dependency:

```scala
  "com.netflix.rxjava" % "rxjava-scala" % "0.14.5",
```

And the proper import:

```scala
  import rx.lang.scala._
```


### Observable

An observable is very easy to create:

```scala
  val intObservable = Observable(1, 2, 3, 4, 5)
      // => 1 2 3 4 5
  val asyncIntObserverable = Observable.interval(50 millis)
      // => 1 2 3 4 5 ... every 50 miliseconds
```

You will notice that Observable creation is very similar to the creation of an Enumerator, they also behave very similarly. Converting an observable and reacting to it in RxJava does not require any extra objects, you can simply invoke functions provided by the observable abstraction:

```scala
  intObservable.reduce(_ + _).subscribe(println(_))
   asyncIntObserverable.map(_ * 2).subscribe(println(_))
```

In the above example we are using some of the built in functions to process the observable streams and react to them via the subscribe method call. The observable abstractions provides functions to map, filter, reduce, combine observables and much more. A description of the [observable functions here](https://github.com/Netflix/RxJava/blob/master/language-adaptors/rxjava-scala/src/main/scala/rx/lang/scala/Observable.scala). I don't plan on going into depth here about Observables, but I highly recommend checking out the [excellent wiki](https://github.com/Netflix/RxJava/wiki/Getting-Started) provided on it's github page.


## RxPlay

Both of these tools are fantastic resources to help with asynchronous reactive programming and they both have a large number of similarities. Given that they are both so similar why not use them together? Does that make sense? There are so many differences between the two libraries, RxJava only has an observable, while Play Iteratees have enumerators, enumeratees, and iteratees. Where would we start?

It turns out that most of the differences between Observables and Iteratees are not nearly as big as they at first seem. Fundamentally they are trying to solve the same problem, and both do so in a similar manner. There is one point where we can create a bridge between the two concepts and that is a bidirection conversion between observables and enumerators.

Let's see how this will work. We'll start by crafting a function to convert an enumerator to an observable.

### Enumerator -> Observable

First let's look at how to create a "custom" observable in rxJava:

```scala
  val customObservable = Observable({ observer[Int] =>
    observer.onNext(1)
    observer.onNext(2)
    observer.onNext(3)
    observer.onNext(4)
    observer.onCompleted()
     new Subscription { override def unsubscribe() = {}}
  })
```

Let's take this apart. First we're passing a function that takes a typed Observer to the apply function on the Obervable object. Then we're repeatedly calling the onNext function of the observer with different values followed by an onCompleted invocation that signifies to the observer that the observable stream has completed. Finally we're returning a subscription that contains an unsubscribe function which we're giving an empty body, since there is no way to cancel or unsubscribe from the observable.

Now that we know how to create an "custom" Observable, let's look at creating a function that converts enumerators to observables:

```scala
  def enumeratorToObservable[T](enum: Enumerator[T]): Observable[T] = {  // 1
    Observable({ observer: Observer[T] =>                                // 2
      enum (
        Iteratee.foreach(observer.onNext(_))                             // 3
      ).onComplete {                                                     // 4
        case Success(_) => observer.onCompleted()                        // 5
        case Failure(e) => observer.onError(e)                           // 6
      }
       new Subscription { override def unsubscribe() = {} }
    })
  }
```

Let's step through what we're doing in the above function.

1. The signature is fairly straight forward here, we're taking in an ```Enumerator[T]``` and returning an ```Observable[T]```
2. Next we create the observerable, passing a function that uses the enumerator to send ```onNext``` messages to the observer.
3. Then we iterate the enumerator using a foreach Iteratee which calls the ```onNext``` method of the observer with each value
4. We invoke the ```onComplete``` method on the future returned from iterating the enum
4. Next we match on the ```onComplete``` success case and tell the observer we're complete
5. Otherwise we notify the observer of an error if we encounter one

Finally we return the subscription, again with no way of unsubscribing. (edit: This is Bad! See [this followup post](http://bryangilbert.com/code/2013/11/03/rx-the-importance-of-honoring-unsubscribe/))

Pretty neat huh? We can now convert enumerators to observables, let's try it out:

```scala
  val enum = Enumerator.fromFile(new File("test.txt"), 1)
  val observer = enumeratorToObservable(enum)
  observer.map(new String(_)).subscribe(x => println())
    // => h e l l o   w o r l d
```

Here we created an enumerator from a text file buffering each byte at a time, converted it into an observer, then using standard observer functions converted each of those bytes into a string and then printed them by subscribing. Sweet!

Bonus points for the fact that the Enumerator doesn't begin pushing values to the observable until an observer is subscribed, so there isn't anything suprising going on when we convert the enumerator to an observable.

With that out of the way let's move on to converting an Enumerator to an Observable.

### Observable -> Enumerator

Now going the reverse let's take a look at how to create an enumerator we can push values into. Thankfully, there's a handy dandy function in the play Concurrent namespace called unicast, let's check it out:

```scala
  val enum = Concurrent.unicast[T](onStart = { chan =>
    chan.push(1)
    chan.push(2)
    chan.push(3)
    chan.push(4)
    chan.end()
  })
```

Unicast creates an enumerator that is populated when values are pushed into it's channel. A function as passed is the ```onStart``` parameter which takes this channel, populates it with 1 through 4, and then signals the end of the input. Pretty familiar huh?

Now that we know how to create enumerators we can push values into, there's one more piece that we need in place. We need to be able to create a custom observer so that we can update our enumerator appropriately. Luckily all we need to do is implement an interface:

```scala
  class PrintObserver[T] extends rx.Observer[T] {
    def onNext(arg: T): Unit = println(arg)
    def onCompleted(): Unit = println("Done!")
    def onError(e: Throwable): Unit = println(s"Error: ${e}")
  }
   Observer(1,2,3,4).subscribe(new PrintObserver())
    // => 1 2 3 4 "Done"
```

Now let's put together something to convert an observable into an enumerator:

```scala
    def observableToEnumerator[T](obs: Observable[T]): Enumerator[T] = {   // 1
      Concurrent.unicast[T](onStart = { chan =>                            // 2
        obs.subscribe(new ChannelObserver(chan))                           // 3
      })
    }
     class ChannelObserver[T](chan: Channel[T]) extends rx.Observer[T] {    // 4
      def onNext(arg: T): Unit = chan.push(arg)                            // 5
      def onCompleted(): Unit = chan.end()                                 // 6
      def onError(e: Throwable): Unit = chan.end(e)                        // 7
    }
```

Let's work through what's going on in this function.

1. The signature is fairly straight forward here, we're taking in an ```Observable[T]``` and returning an ```Enumerator[T]```
2. Create an enumerator using the unicast function which we'll update from the observable
3. Create a new ```ChannelObserver``` using the channel and use it to subscribe to the observable
4. Create a new class ```ChannelObserver``` that extends ```rx.Observer``` and takes in a typed channel
5. On each invocation of ```onNext``` push value onto channel
6. When ```onCompleted``` method is invoked complete the channel
7. When ```onError``` method is invoked complete the channel and pass the error through

Whew! Now we can convert observers into enumerators as well!

```scala
  val asyncIntObserverable = Observable.interval(50 millis)
  val enumerator = observableToEnumerator(asyncIntObserverable)
  enumerator.run(Iteratee.foreach(println(_)))
```

We get bonus points here as well since the ```onStart``` function we provided is only actually executed when an iteratee is attached to the enumerator, so the observable will not begin emitting values until we're ready to handle them.

And there you have it full bidirectional conversion between Enumerators and Observables. 

### Observable <-> Enumerator

Now we have a bridge between these two libraries we can use them both together pretty easily. When these conversions happen they retain the ability to only begin executing when when the subscribers are attached. Additionally both propogate errors between themselves correctly. Finally here is the full code listing for full implict conversions so we can easily use one in place of another:

```scala
  object RxPlay {
    implicit def enumerator2Observable[T](enum: Enumerator[T]): Observable[T] = {
      Observable({ observer: Observer[T] =>
        enum (
          Iteratee.foreach(observer.onNext(_))
        ).onComplete {
          case Success(_) => observer.onCompleted()
          case Failure(e) => observer.onError(e)
        }
         new Subscription { override def unsubscribe() = {} }
      })
    }
     implicit def observable2Enumerator[T](obs: Observable[T]): Enumerator[T] = {
      Concurrent.unicast[T](onStart = { chan =>
        obs.subscribe(new ChannelObserver(chan))
      })
    }
     class ChannelObserver[T](chan: Channel[T]) extends rx.Observer[T] {
      def onNext(arg: T): Unit = chan.push(arg)
      def onCompleted(): Unit = chan.end()
      def onError(e: Throwable): Unit = chan.end(e)
    }
  }
```

I hope this post was able to help someone out there writing reactive programs and perhaps wanting to leverage both of these technologies in different places. At the very least it provides a good basis for examining both of these libraries and seeing through to some of their similarities. Example code from this project can be [found on my github account](https://github.com/gilbertw1/rxplay-example). Thanks for reading this blog post.

Edit: Check out this [follow up post](http://bryangilbert.com/code/2013/11/03/rx-the-importance-of-honoring-unsubscribe/) for an implementation that properly handles unsubscription.