---
title : Using Clojurescript, Macros, and core.async to Escape Callback Hell (Asynchronize)
tags : [clojure, clojurescript, javascript, core.async, macros]
--- 

I've been tinkering around with clojure for the past month or two, using it to write a script here or there at work or
solving the odd problem on project Euler. I recently picked up [Clojure Programming](http://www.amazon.com/Clojure-Programming-Chas-Emerick/dp/1449394701/) and have been reading through it
in my free time, and just made it through the chapter on Macros. Excited to have learned a bit about them, and seeing
how simple they are to write (compared to scala, which I've been working in), I decided to look for a problem to solve.

One of my co-workers recently authored the [suspend](https://github.com/jmar777/suspend) nodejs library which builds on top of ES6 generators. There have been
several of these libraries being written that provide a cleaner way to deal with Node's callback conventions. This seemed
like a perfect place to start. Having played with clojurescript once before I decided to create a macro that would create a 
cleaner interface on top of suspend. It was here I hit my first roadblock, there was no way to use ES6 generators from 
clojurescript (nor should there be!). After that roadblock, I then resolved to create a macro that would create an object
that would match the Iterator interface described in the ES6 spec (when you have a hammer...am I right?).

Finally, I decided not to try and use generators at all, but instead to solve the callback problem using [core.async](https://github.com/clojure/core.async).
I'd been reading a lot about core.async and it seemed like the perfect fit.

## Asynchronize

[Asynchronize](https://github.com/gilbertw1/cljs-asynchronize) is a clojurescript macro that will allow you to write code that uses asynchronous callbacks in a synchronous fashion. Take this
extremely trivial javascript example that prints a file:

```javascript
  var fs = require("fs");
  fs.readFile("file", "utf8", function(err, res) {
    console.log(res)  
  });
```

This is a pretty basic example illustrating the use of a callback function to handle flow control. Once the file has been fully read
the callback will be invoked with the results and we can continue on to do what we were planning to do with its contents.

Here is how it can be rewritten using the Asynchronize macro:

```clj
  (def fs (node/require "fs"))
  (asynchronize
    (def res (.readFile fs "file" "utf8" ...))
    (console/log res)))
```

Not too bad huh? Notice the '...' in the above code, the asynchonize macro uses this placeholder symbol to tell which functions require a 
callback. This was inspired by [To Be Continued](https://github.com/gregspurrier/to-be-continued)

How about reading three files?

```clj
  (asynchronize
    (def f1 (.readFile fs "file1" "utf8" ...))
    (def f2 (.readFile fs "file2" "utf8" ...))
    (def f3 (.readFile fs "file3" "utf8" ...))
    (console/log f1)
    (console/log f2)
    (console/log f3)))
```

That's a little nice, but we can still do this much better

```clj
  (asynchronize
    (def all-contents (map #(.readFile fs % ...) ["file1" "file2" "file3"]))
    (doseq [content all-contents] (console/log content)))
```

Asynchronize also works properly in a nested fashion:

```clj
  (asynchronize
    (def contents (.readFile fs (.readFile fs (.readFile fs "file" "utf8" ...) "utf8" ...) "utf8" ...))
    (console/log contents)))
```

The way the above example works is it reads the content of a file named "file" containing the path to another file,
it then reads the content of that file again containing another file, which it reads the content of and prints to the
console. If you were to write this in javascript, (semantically) it'd look like:

```javascript
  fs.readFile("file", "utf8", function(err, res) {
    fs.readFile(res, "utf8", function(err, res) {
      fs.readFile(res, "utf8", function(err, res) {
        console.log(res);
      });
    });
  });
```

Pretty impressive stuff right? So, you may ask, how many lines of code is this magical macro? 100 lines? 1000 lines?....10000 lines??
No, it's actually only [24 lines of code](https://github.com/gilbertw1/cljs-asynchronize/blob/master/src/cljs_asynchronize/macros.clj)! It works surprisingly well and was written by a complete clojure noob!

That speaks volumes about clojure (and clojurescript) itself and core.async which this macro is built on top of.

## core.async

For those of you who haven't heard of core.async it's an excellent clojure/clojurescript library that provides support for asynchronous
programming using channels. It's modeled closely after go's concurrency model (great post [here](http://blog.drewolson.org/blog/2013/07/04/clojure-core-dot-async-and-go-a-code-comparison/)).

A quick example containing everything you'll need to know about core.async to understand how asynchronize works

```clj
  (let [c (chan)]
    (go (>! c "hello!"))
    (go (console/log (<! c))))
```

In the above example we are creating a channel and assigning it to the variable c. We are then using the go macro which asynchronously
executes it's body. The channel we created can be written to and read from using ">!" and "<!" respectively. The channel is unbuffered
meaning it can only contain a single value at a time. If an attempt is made to write to a channel already containing a value then the go
block will suspend until the value is taken out of the channel. Conversely if a consumer tries to read from a channel that does not
contain a value, it's go block will be suspended until a value is put into the channel.

Pretty simple right? This allows for asynchronous communication using channels....simple, but very powerful.

This is all great stuff, but how does asynchronize benefit from using core.async?

## Under the hood

At it's heart the asynchronize macro is extremely simple. The first thing it does is create a channel to be used throughout the code block
and wraps the code in a go block:

```clj
  (let [c (chan)] ;; uses a unique generated symbol for c
    (go
    (code))) ;; code includes all of the statements passed to the macro
```

We'll use this channel throughout the macro to coordinate between different callbacks and the main go block created here. Next we search through
all of the forms for ones ending with "..." and convert them into a do block which calls the function with a generated callback, and then suspends
itself while waiting for a value in the main channel:

```clj
  ;; Before
  (.readFile fs "file" "utf8" ...)

  ;; After
  (do
    (.readFile fs "file" "utf8" generated-callback) ;; note generated-callback
    (<! c))
```

Notice the generated-callback, this is a very simple function that looks like:

```clj
  (fn [err res]
    (>! c res))
```

This callback basically writes the successful result into the channel. Once the value is written into the channel the original go block will unsuspend
and continue processing.

And there you have it. That's pretty much all there is to asynchronize right now. You can find the source code for it [here](https://github.com/gilbertw1/cljs-asynchronize).
As mentioned earlier, this is the first macro I've written and my first time using core.async other than playing with toy examples, so feel free to 
leave constructive comments :)

In a followup post I plan on detailing how get up and running with core.async, clojurescript, and Asynchronize, as well as dive into the technical 
details of asynchronize.

## Roadmap

- Error Handling
- Execute Async Functions Concurrently (As much as possible)
- Work with promises, thenables