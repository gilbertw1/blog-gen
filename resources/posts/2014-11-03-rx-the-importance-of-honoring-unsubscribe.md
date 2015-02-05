---
title : Rx: The Importance of Honoring Unsubscribe
tags : [scala, rxJava, play2, iteratees]
---

I recently wrote a [blog post](blog/2013/10/22/rxPlay-making-iteratees-and-observables-play-nice) on the similarities between rxJava and Play iteratees and how they could be used seamlessly together using implicit conversions. While implementing either of these concepts in terms of the other was very useful in understanding how they both work and relate to each other, it turns out I was missing a very key compenent that was being lost in translation. While I was uncomfortable with the fact that I was not providing a way to unsubscribe from an observable created from an enumerator, I did not realize the gravity of not doing so. Thanks to Erik Meijer for pointing out that this is definitely [not good](https://twitter.com/headinthebox/status/393650594428104704), which is putting it mildly.

## Why does it matter?

One of the interesting properties of both observables and enumerators is the fact that they do not (normally) start producing data until they have something attached to them that can listen for it. Additionally both have facilities that allow that listener to tell them when they're done listening so that they can stop producing data. While the prior implementation properly dealt with handling initiating streams of data, it did not properly handle ending them when a listener was finished listening.

Terminating data streams when a listener is no longer attached is a critical component of reactive programming. The reason being that it is rather common to deal with potentially infinite streams of data when programming reactively, and an implementation that does not clean up streams that are no longer being consumed can cause major problems. A trivial example using an infinite stream is the following:

```scala
  val squareObs = Observable.interval(10 millis).map(x => x*x).take(10)
  squareObs.subscribe(println(_))
    // => 0 1 4 9 16 25 36 49 64 81
```

Notice that ```Observable.interval(10 millis)``` creates an observable that will produce an infinte stream of incrementing long values starting from 0, yet we only end up producing 10 values. This is because of the method call ```.take(10)``` that we use to create a new Observable which now will only produce 10 items and then terminate. If a way to unsubscribe is not provided by *every* observable created in this chain (interval, map, take), then there will be no way to terminate a stream of events back to it's source when a listener is no longer interested in receiving data from this stream.

In the above example if the observable created by ```Observable.interval(10 millis)``` did not honor the unsubscribe call and did not cease producing data then this observable would continue running and producing data forever (or at least until the process is killed!). This is the problem with the conversions provided in the previous post, every stream would continue running until completion or else...

## Unsubscribing in RxJava

So, now that we know not honoring the unsubscribe function is bad, let's take a look at how it works. Let's jump back to our previous example and see how the ```take``` function behaves.

The take function when invoked on an observable creates a new observable that will only produce a limited number of values based on the int value provided to it. In the case of the above example ```take(10)``` will create an observable that only produces 10 values. How take operates is that when an observer subscribes to the observable that it produces, the observable then subscribes an observer to the source observable that it was created from. As each value is pushed to it's observer it increments an internal counter and then pushes the value on to it's subscriber. Once the counter reaches the specified count (in our case 10) the take observer unsubscribes from the source observable and invokes the ```onComplete``` function on the observer listening to it. If at any time the subscriber unsubscribes from it, it simply unsubscribes from the source observable.

If you jump back one step on the invocation chain, you'll notice ```map(x => x*x)```. Map behaves very similarly to take, instead of incrementing a count and potentially completing as each value passes through, it simply transforms the value and pushes it on to it's subscriber. It will also correctly unsubscribe from it's source observable when it itself is unsubscribed from. The full observable chain from the example can be visualized as such:

<img alt="Observable Chain" height="350" width="700" src="/images/observable-chain-diagram.jpg">

Notice that in order to properly unsubscribe from and terminate the original observable that is producing data, the entire chain has to properly respect and call it's superiors unsubscribe function. Otherwise, the unsubscribe call chain would not make it all the way back. If this were to happen the original observable would merrily continue along producing data even though no one would be listening any longer. 

Now that we understand the importance of a properly implemented unsubscribe function in RxJava, let's look how Play Iteratees tackle the same problem.

## Unsubscribing with Play Iteratees

Play Iteratees solve the problem of unsubscribing in a very different manner. I mentioned that Iteratees are a state machine in my previous post but didn't really elaborate much on the subject. Every time an iteratee is provided with a value it returns a new state. These states are:

1. ```Cont``` - Indicates that the iteratee is still accepting input
2. ```Done``` - Indicates that the iteratee is finished accepting input
3. ```Error``` - Indicates that the iteratee has encountered an error

This indicates upstream how the enumerator should behave. So, when we only want to take 10 items when using iteratees, we simply return a done state once this criteria has been met and this allows us to terminate producing more input. This is a bit of a simplification (again I encourage you to check out [this post](http://mandubian.com/2012/08/27/understanding-play2-iteratees-for-normal-humans/) if you haven't already), but it gives us a basic understanding of how this unsubscription process works.

## Properly handling unsubscription

Now that we've talked about how unsubscription works in both RxJava and Play Iteratees, let's take a stab at keeping those lines of communication open while performing our transformations. In these code examples I'm only going to cover the changes that I made to support unsubscription, for full context review [this post](blog/2013/10/22/rxPlay-making-iteratees-and-observables-play-nice).

### Enumerator -> Observable

Let's start with the ```Enumerator -> Observable``` conversion:

```scala
  implicit def enumerator2Observable[T](enum: Enumerator[T]): Observable[T] = {
    Observable({ observer: Observer[T] =>
      var cancelled = false                                                       // 1
      val cancellableEnum = enum through Enumeratee.breakE[T](_ => cancelled)     // 2
       cancellableEnum (                                                           // 3
        Iteratee.foreach(observer.onNext(_))
      ).onComplete {
        case Success(_) => observer.onCompleted()
        case Failure(e) => observer.onError(e)
      }
       new Subscription { override def unsubscribe() = { cancelled = true } }      // 4
    })
  }
```


1. Here we're creating a mutable variable ```cancelled``` and initialized it to false.
2. Next we thread the enumerator through an enumeratee ```breakE``` which will pass input through until it's predicate is satisfied. Irregardless of it's input values this enumerator will now continue producing until the cancelled variable is set to false.
3. We use the ```cancellableEnum``` to feed our observer.
4. We override the unsubscribe function on the subscription we return and set ```cancelled``` to true.

Now, when a subscriber calls ```unsubscribe``` on the new observable the enum underneath will break on the next value and the enumerator will stop producing values. We can now create observers from enumerators that respect unsubscribe!

### Observable -> Enumerator

Now let's look at improving our ```Observable -> Enumerator``` conversion. This one was a bit more difficult primarily since the ```Concurrent.unicast``` function we were using has some limitations that affect our specific case. What we want to do is capture the subscription when we subscribe to the Observable and unsubscribe from that subscription when the iteratee completes or encounters an error. ```Concurrent.unicast``` takes three parameters ```onStart```, ```onComplete```, and ```onError```, ```onStart``` being called when an Iteratee is attached to the resulting Enumerator. While at first glance these functions seem ideal for our use case, it turns out that there is no way for the ```onComplete``` or ```onError``` functions to know which "iteration" they're being called for. Since a major property of Enumerators is the fact that one can be run multiple times, and therefore having ```onStart``` invoked multiple times, there is no way to tie a completion or error back to a particular subscription.

To illustrate this point, let's look at a naive solution (which I initially wrote :) ):

```scala
  implicit def observable2Enumerator[T](obs: Observable[T]): Enumerator[T] = {
    var subscription: Option[Subscription] = None                               // 1
    Concurrent.unicast[T](onStart = { chan =>
      subscription = Some(obs.subscribe(new ChannelObserver(chan)))             // 2
    }, onComplete = {
      subscription.foreach(_.unsubscribe)                                       // 3
    }), onError = { (_,_) =>
      subscription.foreach(_.unsubscribe)                                       // 4
    }
  }
     class ChannelObserver[T](chan: Channel[T]) extends rx.Observer[T] {
    def onNext(arg: T): Unit = chan.push(arg)
    def onCompleted(): Unit = chan.end()
    def onError(e: Throwable): Unit = chan.end(e)
  }
```


1. We create a (gasp!) mutable variable subscription which holds an optional ```Subscription```
2. When we subscribe to the observer we capture the subscription in the previously defined subscription variable
3. When the ```onComplete``` function is invoked we unsubscribe from the subscription if it exists (which it should!)
4. Same as #3 exception when ```onError``` is invoked.

Notice that this naive solution only allows for a single Iteratee to be attached to the resulting enumerator at a given time, and hence is not a thread safe solution.

Ok, so how do we create a thread safe enumerator that we can unsubscribe from? Well unfortunately I did not see an easy way to do this using built in Iteratee helpers, so I ended up modifying and creating my own unicast function. My implementation of the unicast function takes a single ```onStart``` function as a parameter. The ```onStart``` function takes a channel just like before, however instead of returning ```Unit```, it returns a tuple of two functions, an ```onComplete``` and an ```onError``` function. These completion and error functions are called when the iteration that created them is completed or errored out. This way we can properly clean up a subscription when the iteration that is using it is completed. I'm not going to cover the new unicast function here, but you can find it along with all the code in this post on my [github account](https://github.com/gilbertw1/rxplay-example/blob/master/src/main/scala/Test.scala).

Here's what the new conversion function looks like:

```scala
  implicit def observable2Enumerator[T](obs: Observable[T]): Enumerator[T] = {
    unicast[T] { (chan) =>                                                      // 1
      val subscription = obs.subscribe(new ChannelObserver(chan))               // 2
      val onComplete = { () => subscription.unsubscribe }                       // 3
      val onError = { (_: String, _: Input[T]) => subscription.unsubscribe }    // 4
      (onComplete, onError)                                                     // 5
    }
  }
     class ChannelObserver[T](chan: Channel[T]) extends rx.Observer[T] {
    def onNext(arg: T): Unit = chan.push(arg)
    def onCompleted(): Unit = chan.end()
    def onError(e: Throwable): Unit = chan.end(e)
  }
```


1. We're invoking the (custom) unicast function with an onStart function that take a channel and returns a tuple of two functions
2. We capturing the subscription when we subscribe to the observer
3. Next we're creating an onComplete function that unsubscribes from the subscription
4. Then we're creating an onError function that will also unsubscribe from the subscription
5. Finally we're returning both the onComplete and onError functions so that they can be called when the current iteration completes or errors

And there you have it a working ```Observable -> Enumerator``` conversion that properly handles unsubscription. One note, if anyone knows a better way to achieve this than hand rolling my own unicast function please let me know!

## Conclusion

I personally was overly focused on a single path of communication, that is ```Producer -> Subscriber``` and did not fully appreciate that in Rx the lines of communication move both ways. I hope that now if you, like me, thought about Rx this way now have a deeper appreciation for the fact that Rx is not just a one way stream of data. All of the code in this post [can be found here](https://github.com/gilbertw1/rxplay-example), thanks for reading!