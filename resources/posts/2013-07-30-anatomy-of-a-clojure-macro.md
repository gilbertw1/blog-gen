---
title : The Anatomy of a Clojure Macro
tags : [clojure, clojurescript, macros, tutorial]
---

One of the great things about Clojure is the fact that we can (relatively) easily extend the language at compile time
using macros. I've recently implemented (and [written about](http://bryangilbert.com/code/2013/07/19/escaping-callback-hell-with-core-async/))
one such macro that creates a simple interface over node.js functions that require callbacks to continue the flow of execution.
Using this macro as a basis I hope to walk through the entire process of creating a clojure macro from inception to implementation,
and along the way cover everything that is required for you as a reader (and potentially being new to Clojure) to create a non-trivial
macro of your own. 

I am writing this post because the macro I'll be dissecting is the very first clojure macro I've ever written, and I hope it will be instructive
to anyone wanting to write their own macro but does not know where to start. I'll also cover a few pitfalls and helpful things that I found
out along the way. However, before we start playing the game we need to first introduce the players.

## The Players

The constructs that I'm about to cover are essential tools that we need to create a macro. They allow us to work with code as if it were data
(which it is!) and easily choose what to evaluate and what to emit as symbols.

### Homoiconicity

Man that's a cool word!

Clojure is a homoiconic language and this is a very important fact which enables us to write macros for it. This effectively means that clojure 
code is expressed in terms of simple data structures that are primitives in the language. Take for example the following code:

```clj
    (+ 1 1) ; => 2
```

Bet you can't figure out what that does! To walk through the example we take a function "+" and apply two parameters to that function, "1" and
"1". The "+" function adds both parameters together and returns a result of 2. This is a standard function invocation which is typical of what 
we'd normally see in clojure code. However, it is also a simple data structure....a list!

If the above function were treated purely as a data structure and not evaluated it would simply be a list containing three items: "+", "1", and
"1". Needless to say if the code you write is a data structure just like you manipulate normally in the language, then it too can be manipulated
by code you write!

This is one of the most important things to know about clojure...code is simply data structures through and through. This leads us to our
next topic:

### Quote

You may be wondering at this point, how can we prevent code from being evaluated by the reader so that we can work with it as data? The answer is 
the quote special form which allows us to signal to the reader that an expression is not meant to be evaluated as code, but instead be read in as 
data. The quote special form can be invoked either by name ```quote``` or by a apostrophe symbol ```'```. For example:

```clj
    (+ 1 1)         ; => 2
    '(+ 1 1)        ; => (+ 1 1)
    (quote (+ 1 1)) ; => (+ 1 1)

```

As you can see from the example above by quoting the expression ```(+ 1 1)``` we are effectively telling the reader to treat the entire expression
purely as data. This works for symbols (names of functions or variables) or arbitrary expressions.

```clj
    '+                 ; => +
    'sum               ; => sum
    '(+ (+ (+ 1) 2) 3) ; => (+ (+ (+ 1) 2) 3)

```

At this point any of the above expressions could be provided directly to a function and manipulated by said function. They are simply unevaluated lists
and symbols at this point

```clj
    (reverse '(+ 1 1)) ; => (1 1 +)
    (map reverse '((+ 1 1) (+ 2 2) (+ 3 3))) ; => ((1 1 +) (2 2 +) (3 3 +))

```

Using very basic clojure core functions we are effectively rearranging and altering code. If we were to evaluate the above code after the transformation
it would mean something very different than before we performed our transformation. This is basically what macros do! A macro is simply a function that
runs after code is read, but before it is evaluated. This way we can tranform the code in whatever (devious?) ways we desire! But more on that shortly,
first we have some more players that need introducing.

### Syntax Quote

While the quote special form is great, it leaves no simple way to "piece" together expressions that contain values that need to be evaulated. For example
if we had a variable that we wanted to put inside of an unevaluted expression, we'd have to manually construct a list

```clj
    (def x 5)
    '(+ 1 x)       ; => (+ 1 x)
    (list '+ '1 x) ; => (+ 1 5)

```

As you can see putting a symbol inside of a quoted expression does not evaluate x as you may expect, but instead retains x's "symbolic" value. In the second 
expression we create a list containing the evaluated value of x, however this syntax is fairly cumbersome and becomes even more so as the expressions we're
working with become more complicated.

The true power (and difficulty!) of macros is being able to intermix evaluated values and symbolic values at compile time. This way we can reorganize code
using functions and variables that allow us to adequately work with these data structures while leaving certain symbols to be evaluated at a later time (read:
runtime). I'll go into this in depth later in the post, for now let's inroduce syntax quote already!

Syntax quote is a special form denoted by the backtick character ``` ` ``` and when applied to an expression tells the reader to treat the expression as data
just like the quote special form does, except that the reader also fully qualifies all of the symbols.

```clj
    `sum       ; => user/sum
    `(+ 1 1)   ; => (clojure.core/+ 1 1)
    `(+ x y z) ; => (clojure.core/+ user/x user/y user/z)

```

On the surface the only real difference between the syntax quote and quote special forms is that the symbols are all now fully namespaced. This is very important
to us as macro writers because we want to make sure that all of our symbols are correctly namespaced. Since most of the code we're working with is unevaluated, 
nothing will tell us if we're emitting symbols that don't exist or are in the wrong namespace since they won't be evaluated until the code is run. In the above
example the "+" function exists in the "clojure.core" namespace, and the symbols x, y, z, and sum are referring to the local scope.

You may be asking at this point, how does syntax quote help out with conditional evaluation? Well there two additional
forms that can be used within a syntax-quote that can turn it into a code "template" of sorts, unquote and splicing unquote.

#### Unquote

Within a syntax quoted block we can conditionally unquote expressions using the tilde character ```~```. This basically allows us to cherry pick which expressions 
are evaluated and all the rest are left unevaluated. Revisiting our earlier example we can eliminate need to manually construct a list of symbols.

```clj
    (def x 5)
    '(+ 1 x)       ; => (+ 1 x)
    (list '+ '1 x) ; => (+ 1 5)
    `(+ 1 ~x)      ; => (clojure.core/+ 1 5)

```

In the above syntax quoted example we told the reader to only evaluate the symbol x in the expression, resulting in an expression containing the value of x, which
is 5, instead of the symbol x itself. This works as expected with arbitrary expressions

```clj
    `(+ (+ 1 1) 1)  ; => (clojure.core/+ (clojure.core/+ 1 1) 1)
    `(+ ~(+ 1 1) 1) ; => (clojure.core/+ 2 1)

```

This is a nice way to denote which symbols to evaluate and which to not, however there is another helpful form that we can use within a syntax quote.

#### Splicing Unquote

The splicing unquote form ```~@``` allows us to evaluate the expression exactly as the normal unquote does, however it expects the result of the evaluation
to be a list or sequence which it then splices into the outer list at the position it appeared. This sounds complicated, but an example should help show exactly
how this form behaves.

```clj
    `(1 2 ~(list 3 4))     ; => (1 2 (3 4))
    `(1 2 ~@(list 3 4))    ; => (1 2 3 4)
    `(+ 1 ~@(reverse `(+ 1 1))) ; => (clojure.core/+ 1 1 1 clojure.core/+)

```

In the example above we can see that the reader is evaluating the expression that is unquoted by the splicing unquote, but that it is also merging the values
from the evaluated list into the outer list. 

This tops off the basics of syntax quoting nicely, we'll put it to much more advanced usage later.

### Generated Symbols

When we are manipulating and creating code at compile time, we'll often want to create temporary variables that the generated code uses. Since the macro code
will be evaluated at runtime within a different scope, we need to be sure that we are not creating variables that conflict with the code that we are altering.
Otherwise we may clobber symbols defined in the runtime scope or vice versa. To prevent this clojure provides us a function ```gensym``` which evaluates to a 
guarnanteed unique symbol

```clj
    (gensym)         ; => G__1216
    (gensym)         ; => G__1219
    (gensym "myvar") ; => myvar1222

```

However, creating a generated symbol and assigning it to a resusable variable can be very verbose when used inside of a macro, especially if done multiple times
and at different scopes. To make this much nicer clojure provides nice syntax to allow unique symbols to be defined inside of a syntax quoted form using the hash
symbol ```#```. For example

```clj
    (gensym)         ; => G__1216
    `x#              ; => x__1223__auto__
    `(+ x# x#)       ; => (clojure.core/+ x__1226__auto__ x__1226__auto__)
    `(+ y# y#)       ; => (clojure.core/+ y__1229__auto__ y__1229__auto__)

```

Notice that any symbol inside of a syntax quoted expression that ends with a # is converted to unique symbol. One important thing to note is that all variables 
of the same name followed by a # inside of the same syntax quoted expression refer to the same unique symbol. This way we can refer to the same symbol multiple 
times without having to explicity create a variable. However, if you need to use the same unique symbol across multiple syntax quoted expressions then we have no
choice but to create a variable to hold the symbol.

```clj
    (def x (gensym))  ; => G__1232
    (def y (gensym))  ; => G__1233
    `(+ ~x ~y)        ; => (clojure.core/+ G_1232 G_1233)
    `(+ ~y ~x)        ; => (clojure.core/+ G_1233 G_1232)

```

Two things I want to bring up at this point. One is that I've been using ```def``` a lot. This is highly discouraged practice in clojure since variables created
with ```def``` are global and mutable. A much better way to create temporary immutable variables is to use the ```let``` macro. I'm only using ```def``` pervasively
in these examples because it is simpler to follow using a repl. Secondly, notice how ugly the variable names are that are generated....don't be scared by this. We
will never be using these variable names directly, they only exist to store temporary values and will never actually be seen by anyone (unless your using ```macroexpand-1```).

### macroexpand-1

Speaking of ```macroexpand-1```, this is the final tool in our macro creating toolbox that we'll be exploring. This function is by far our best tool for debugging
the macros that we write. ```macroexpand-1``` basically takes an expression, and if it contains a macro form it will expand it. By expand I mean it will evaluate the
macro and return the resulting code after the macro has transformed it. 

```clj
    (if true 1 2)                       ; => 1
    (if-not true 1 2)                   ; => 2
    (macroexpand-1 '(if-not true 1 2))  ; => (if (clojure.core/not true) 1 2)

```

In the above example we are expanding an expression that uses the "if-not" macro. The behavior of the "if-not" macro should be obvious given the result of the macro
expansion, it uses the logical opposite of the boolean test to determine which branch to execute. Also, note the expression being passed to the macro expand function
is quoted, if we had not quoted it then the reader would have evaluated the function in place.

As the 1 on the end designates, ```macroexpand-1``` will only perform a single expansion. It's actually preferred this way since we often return code that uses more 
macros from a macro. If the macros were recursively evaluated it would be much harder to determine the direct output of our macro.

This concludes our discussion of the tools used to create macros. There are a few more out there that are very useful, however we have covered everything we need to 
create our first macro and analyze the asynchronize macro.

## Start to Finish: A (fairly) Simple Macro

So now that we've spent some time and walked through each the tools needed to write a macro, let's pull them all together and write a simple macro. First we'll want 
figure out exactly what we want our macro to achieve. 

### What should it do?

In this case we'll create a macro that takes the result of each expression in it's body and uses it as a replacement for the "_" character in the next expression. If
your familiar with the [threading macros](http://blog.fogus.me/2009/09/04/understanding-the-clojure-macro/) in clojure, this performs a similar function. Now that we've
decided what we want our macro to do, let's figure out how we want our code to look. If your still foggy on what this macro will do this example should help.

```clj
    (->>> 1       ; initial value of 1
      (+ _ 2)     ; with previous result of 1, semantically looks like: (+ 1 2)
      (+ 3 _)     ; previous result of 3, (+ 3 3)
      (- 50 _)    ; previous result of 6, (- 50 6)
      (/ _ 2))    ; previous result of 44, (/ 40 2) => final result: 22

```

So we want our macro to be named ```->>>``` and take an initial value (in the example our initial value is 1) followed by any number of expressions representing the body. 
We are using the underscore as a place holder for the result of the previous expression in each of these expressions. The initial value is to be substituted in the underscore
of the first expression (```(+ _ 2) => (+ 1 2)```), then the result of that expression is used to replace the underscore in the next expression (```(+ 3 _) => (+ 3 3)```) and
so on and so forth.

This is pretty cool we just built the structure of our macro entirely around what we want our code to look like! This normally feels to me like the hardest part of writing a 
macro.

### What should the resulting code look like?

Now that we've defined how we want to write our code and what it should do, we now need to figure out what the code our macro is going to create should look like. There are
a number of different ways we could structure the result code to achieve what we want, however we're going to go with a nice recursive let based structure. Let's take a stab
at what the resulting code should look like.

```clj
    ;; Before
    (->>> 1       
      (+ _ 2)     
      (+ 3 _)     
      (- 50 _)    
      (/ _ 2))    

    ;; After (macroexpansion)
    (let [init 1]
      (let [res0 (+ init 2)]
        (let [res1 (+ 3 res0)]
          (let [res2 (- 50 res1)]
            (let [res3 (/ res2 2)]
              res3)))))

```

The key to understanding the above code is understanding the let macro (that's pretty much all there is!). The let macro consists of a binding vector and a body of expressions.
A binding vector is just a vector containing an even number of elements with a variable name followed by a value repeated. For example ```(let [x 2 y 3] ...)``` binds 2 to the symbol
x and 3 to the symbol y. Everytime x or y is referred to in the body (denoted by ...), it is substituted with 2 or 3 respectively.

Now back to our post expansion code. We are initially binding the init symbol to 1 (our initialization value), and each time we evaluate a new statement we're substituting the 
previous let bound value where the underscore used to be, and binding it to a new unique variable to be substituded in the next expression. Notice that the in the last let expression
we're simply returning the final result.

Note that we could have simply put this all in a single let expression.

```clj
    ;; After (macroexpansion)
    (let [init 1
          res0 (+ init 2)
          ...]
      res3)

```

However, I'll leave that as an exercise to the reader.

### Creating the macro (good gawd...finally!)

Now let's tap into everything we've learned so far and write this macro!....but first let's write a few helper functions :) 

First thing we know we need to do, is replace an underscore with a value. Let's write a function to do that now.

```clj
    (defn replace-if-underscore [element val]
      (if (= element '_)
        val
        element))

    (replace-if-underscore '_ 1) ; => 1
    (replace-if-underscore '+ 1) ; => +

```

This simple function takes two arguments, an element and a value. If the element is equal to the symbol underscore ```'_``` then we return the value to replace the underscore, 
otherwise we just return the element since we don't want to replace it. Now that we've done that, let's define a function to replace all underscores that may exist in a form.

```clj
    (defn replace-underscores [form val]
      (map #(replace-if-underscore % val) form))

    (replace-underscores '(+ 2 _) 1) ; => (+ 2 1)
    (replace-underscores '(+ 2 3) 1) ; => (+ 2 3)

```

This function takes an entire form (think list...```(+ 1 2)``` is a just a list) and a value. It maps the "replace-if-undescore" function across every element in the form which 
either replaces the element if it's an underscore, or retains the element if it's not.

Note that "replace-underscores" does not recursively replace underscores in subforms, if we wanted this to be a robust implementation we'd want to implement it that way so that it
would not stop after replacing underscores in the topmost form. Again, I'll leave that as an exercise to the reader (hint: [clojure.walk](http://richhickey.github.io/clojure/clojure.walk-api.html)).

Now, we only need one more helper function before we create our macro. We'll want this function to recursively convert each of the expressions passed to the macro into a let statement.
This function will take two parameters, the rest of the forms in our body and the result value of the previous expression.

```clj
    (defn convert-forms [val [next-form & other-forms]]               ; 1
      (if (nil? next-form)                                            ; 2
        val                                                           
        (let [next-val (gensym)]                                      ; 3
          `(let [~next-val ~(replace-underscores next-form val)]      ; 4
             ~(convert-forms next-val other-forms)))))                ; 5

```

We'll step through this function line by line.

**1)** The interesting thing in the function definition is the fact that we are desctructuring the second parameter (the list of forms) into two variables, the first form in the list and
the rest of the forms in the list.

```clj
    (defn convert-forms [val [next-form & other-forms]]

```

**2)** We're checking to see if next-form, the first form in the list, is nil. If it is, then the list is empty and we are done and just need to return the last value.

```clj
      (if (nil? next-form)  
        val

```

**3)** If we're here then there is another form then we need to generate a unique symbol to hold the result of evaluating that form with the underscores replaced.

```clj
        (let [next-val (gensym)]

```

**4)** Here we are going to generate some code. We are generating a let statement here using a syntax quote. On this line we're evaluating the "next-val" variable which currently holds the unique
symbol that we want to use to store the current evaluation's result. We also need to execute the "replace-underscores" function on the next form in the list, to replace all underscores with the
previous value. Note that while we are evaluating the "replace-underscores" function with the next form as a parameter, it is merely transforming the code and returning it. So the code / data structure
it returns is being placed directly into the syntax quoted block, and is left unevaluated.

```clj
          `(let [~next-val ~(replace-underscores next-form val)]      

```

**5)** Finally we are going to recursively call the "convert-forms" function with the newly let bound value and the rest of the forms. This will continue converting each form until we reach the end of
the list of forms.

```clj
             ~(convert-forms next-val other-forms)))))

```

Throughout the function above take note of which expressions are unquoted (with the tilde) and which are not. "next-val" is a let bound variable containing a generated symbol, so naturally we
want this variable evaluated during compile time so the generated symbol is emitted into the code. Similarily, when we call our helper functions, we want them evaluated at compile time as well
so that the code is generated properly. If we were to omit any of the unquotes then the exact expressions above would be evaluated at run time during which they would have no meaning.

Let's test this function.

```clj
    (convert-forms 2 '((+ _ 1) (+ 4 _))) ; => 
      ; (clojure.core/let [G__1166 (+ 2 1)] 
      ;   (clojure.core/let [G__1167 (+ 4 G__1166)] 
      ;     G__1167))

```

It looks like this function correctly created our let statements from an initial value and a list of forms. Congratulations! We just created our first macro!

Oh wait...

```clj
    (defmacro ->>> [init & forms]
      (convert-forms init forms))

```

Congratulations! We just created our first macro!

Notice that all of the functionality of the macro resides in pure functions that just transform normal data structures (although mostly lists). Our macro simply delegates to these functions and returns
their result, which the reader will evaluate later. Now our example functions exactly as we expect!

```clj
    (->>> 1       
      (+ _ 2)     
      (+ 3 _)     
      (- 50 _)    
      (/ _ 2))  ; => 22

```

You can find the full macro [source code here](https://gist.github.com/gilbertw1/6108019).

## Asynchronize

Now that we have all the tools needed to create a macro, and have in fact created one of our very own, we are now equiped to walk through the source code of the asynchronize macro. If you haven't already
please to a quick runthrough of my [post on asynchronize](http://bryangilbert.com/code/2013/07/19/escaping-callback-hell-with-core-async/) to understand what exactly it does.

There shouldn't be anything too surprising here, in fact asynchronize is not much longer than the threading macro we just created. I'll start off exactly the same as we did with the previous macro, except
with a little less fan fare.

### What should it do?

The goal of asynchronize is to allow us to write synchronous looking code that interops with callback based functions in node.js. Under the covers we'll still be invoking functions
with callbacks, however we'll use [core.async](http://clojure.com/blog/2013/06/28/clojure-core-async-channels.html) to help us hide that fact. To be more specific we want to be able 
to use elipsis to denote where callbacks should exist and have the macro handle generating a callback and ensuring that a value is returned in place for the function invocation when
the callback is invoked with success or an error is thrown when it is invoked with a err argument.

```clj
    (asynchronize
      (def f1 (.readFile fs "file1" "utf8" ...))
      (def f2 (.readFile fs "file2" "utf8" ...))
      (def f3 (.readFile fs "file3" "utf8" ...))
      (console/log f1)
      (console/log f2)
      (console/log f3))

```

Tall order huh?

### What should the resulting code look like?

This is where core.async really helps us. First we want to wrap all of the forms in a go block, which means the entire thing is executed asynchronously and we can suspend the block when we read from a channel.
The general workflow for each of the asynchrous calls contained inside the block are as follows:

1. We want to create two channels, one for successful results and one for errors, sc and fc respectively.
2. Next we want to generate a callback function which needs to write to the success channel if it receives a success result, otherwise writes to the failure channel if it receives an error.
3. We then invoke the function that takes a callback with our generated callback function.
4. Finally we use alts! to select the first channel to return, if the channel that returned first is the success channel then we simply return the value, otherwise we know the failure channel returned
the value so throw an exception with the value inside of it. In either case we want to close both channels

This allows the function we pass the callback to to send values back to our go block via the callback function, after which our go block resumes using the value returned from the function.

```clj
    ;; Before Expansion
    (asynchronize
      (def f1 (.readFile fs "file1" "utf8" ...))
      (console/log f1))

    ;; After Expansion
    (go                                                               ; 1  
      (def f1 (let [sc (chan) fc (chan)]                              ; 2    
                (do
                  (.readFile fs "file1" "utf8" (fn [err res]          ; 3   
                                                 (go
                                                   (if err
                                                     (>! fc err)
                                                     (>! sc res)))))
                  (let [[v c] (alts! [sc fc])]                        ; 4
                    (try
                      (if (= c sc)                                    
                        v                                             ; 5  
                        (throw (js/Error. v)))                        ; 6
                      (finally                                         
                        (close! sc)                                   ; 7
                        (close! fc)))))))
      (console/log f1))

```


**1)** Wrap everything in go block

```clj
    (go 

```

**2)** Define success and fail channels

```clj
      (def f1 (let [sc (chan) fc (chan)]

```

**3)** Invoke function with generated callback in place of elipsis

```clj
                  (.readFile fs "file1" "utf8" (fn [err res] 
                                                 (go
                                                   (if err
                                                     (>! fc err)
                                                     (>! sc err)))))

```

**4)** Select first value and channel (v & c) that gets written to

```clj
                  (let [[v c] (alts! [sc fc])]

```

**5)** If first channel to return is the success channel then return value

```clj
                      (if (= c sc)
                        v 

```

**6)** If first channel to return is not the success channel then we throw an exception (err was returned)

```clj
                        (throw (js/Error. v)))

```

**7)** Cleanup / Close both channels

```clj
                      (finally 
                        (close! sc)
                        (close! fc)))))))

```

Hope your still sticking with me at this point...there's light at the end of the tunnel!

### Creating the macro

Just as before let's start with a few helper functions. Let's start with a function that generates callbacks for us.

```clj
    (defn callback [sc fc]
      `(fn [err# res#]
        (cljs.core.async.macros/go
          (if err#
            (~'>! ~fc err#)
            (~'>! ~sc res#)))))

    (callback 'sc 'fc)  ; => 
      ; (clojure.core/fn [err__1168__auto__ res__1169__auto__] 
      ;   (cljs.core.async.macros/go 
      ;     (if err__1168__auto__ 
      ;       (>! fc err__1168__auto__) 
      ;       (>! sc res__1169__auto__))))

```

This function takes two channels, a success channel and a fail channel. It then generates a function that takes two parameters, a success and an error. If the error is not
nil then we write the error to the fail channel, otherwise we write the result to the success channel. Notice that this function is making use of auto generated unique symbols
using the # character. In the above function err# corresponds to the same unique symbol throughout. Also, notice that the only values we're unquoting here are the success and
fail channels passed into the function (```~'>!``` is a special case, the go macro expects this to not be namespaced so, we're simply evaluating the symbol here).

Next we'll create a function that will create the code to handle returning a successful result or throwing an error depending on which channel is written to first.

```clj
    (defn success-value-or-throw [sc fc]
      `(let [[v# c#] (~'alts! [~sc ~fc])]
          (try
            (if (= c# ~sc)
              v#
              (throw (js/Error. v#)))
            (finally
              (cljs.core.async/close! ~sc)
              (cljs.core.async/close! ~fc)))))

    (success-value-or-throw 'sc 'fc) ; =>
      ; (clojure.core/let [[v__1178__auto__ c__1179__auto__] (alts! [sc fc])] 
      ;   (try 
      ;     (if 
      ;       (clojure.core/= c__1179__auto__ sc) 
      ;         v__1178__auto__ 
      ;         (throw (js/Error. v__1178__auto__)))
      ;       (finally 
      ;         (cljs.core.async/close! sc) 
      ;         (cljs.core.async/close! fc))))

```

Nothing new in this function, again we are creating auto generated unique symbols with the hash symbol, and again we're only really evaluating the success and fail channel 
parameters passed in. You may start to notice a pattern here...we're basically creating code templates with the syntax quoted forms and only evaluating the "dynamic" values that are
being passed in.

Next we'll create a tiny helper function that will take a form and an argument and will add it to the end of the form.

```clj
    (defn add-argument-last [form arg]
      `(~@form ~arg))

    (add-argument-last '(+ 1 2) 3) ; => (+ 1 2 3)
```

This function creates a data list by using the syntax quote and splices the current form into the list using the splicing unquote, and then adds the argument at the end of the
list after the spliced in form by unquoting it. We'll use this function to add the generated callback to the end of the form that previously contained an elipsis.

This finally brings us to the meat of our macro which I'll walk through below.

```clj
    (defn transform [forms]
      (if (list? forms)                                                                  ; 1
        (if (= (last forms) '...)                                                        ; 2  
          (let [sc (gensym) fc (gensym)] ; sc -> success, fc -> fail                     ; 3
            `(let [~sc (cljs.core.async/chan) ~fc (cljs.core.async/chan)]                ; 4
               (do 
                 ~(add-argument-last (map transform (butlast forms)) (callback sc fc))   ; 5
                 ~(success-value-or-throw sc fc))))                                      ; 6
          (map transform forms))                                                         ; 7
        forms))                                                                          ; 8

```


**1)** We want to recursively walk through all of our forms looking for forms that end in elipsis. Our search is indiscriminate so we want to check if the forms parameter is actually a list, if
if it is not, then we just return the argument.

```clj
      (if (list? forms)

```

**2)** If it is a list then we want to check to see if the last form is an elipsis symbol. If it is then we want to transform the list into one that will appropriately handle the callback. Otherwise we
just want to recursively map this transform function over all of the elements in the form.

```clj
        (if (= (last forms) '...)

```

**3)** Here we generate symbols to use for the success channel and the fail channel and let bind them

```clj
          (let [sc (gensym) fc (gensym)] ; sc -> success, fc -> fail

```

**4)** Here we start our syntax quote and open with generating the let binding expression for the success and fail channels

```clj
            `(let [~sc (cljs.core.async/chan) ~fc (cljs.core.async/chan)] 
               (do 

```

**5)** On this line we're dropping the last element in the form (the elipsis), transforming all elements left in the list (in case they contain callback expressions), and adding the 
generated callback as the last argument in the form.

```clj
                 ~(add-argument-last (map transform (butlast forms)) (callback sc fc)) 

```

**6)** Next we're generating the "success-or-throw" block of code and passing in the success and fail channels.

```clj
                 ~(success-value-or-throw sc fc))))

```

**7)** This brings us back to the else case of the if statement that checks if our expression has an elipsis as the final argument. If we reach this case then it did not and we want to 
recursively map this function to all the elements in the form.

```clj
          (map transform forms))

```

**8)** If we reach this case then the forms argument passed in was not a list and we simply want to return the argument.

```clj
        forms))

```

Now let's test this function to verify that it does what we expect.

```clj
    (transform '(def f1 (.readFile fs "file1" "utf8" ...)))  ; =>
      ; (def f1 
      ;   (clojure.core/let [G__1189 (cljs.core.async/chan) G__1190 (cljs.core.async/chan)] 
      ;     (do 
      ;       (.readFile fs "file1" "utf8" (clojure.core/fn [err__1168__auto__ res__1169__auto__] 
      ;                                      (cljs.core.async.macros/go 
      ;                                        (if err__1168__auto__ 
      ;                                          (>! G__1190 err__1168__auto__) 
      ;                                          (>! G__1189 res__1169__auto__))))) 
      ;       (clojure.core/let [[v__1178__auto__ c__1179__auto__] (alts! [G__1189 G__1190])] 
      ;         (try 
      ;           (if (clojure.core/= c__1179__auto__ G__1189) 
      ;             v__1178__auto__ 
      ;             (throw (js/Error. v__1178__auto__))) 
      ;           (finally 
      ;             (cljs.core.async/close! G__1189) 
      ;             (cljs.core.async/close! G__1190)))))))

    (transform '(+ 1 2 3))  ; => (+ 1 2 3)
    (transform '+)          ; => +

```

Everything seems to be looking good based on the output above. The statements containing elipsis are correctly being replaced and callbacks generated correctly. The transform function is
also correctly leaving forms not containing an elipsis unchanged.

Whew! That's pretty much all there is to the asynchronize macro, all we have left to do now is create it!

```clj
    (defmacro asynchronize [& forms]
      `(cljs.core.async.macros/go
        ~@(map transform forms)))

```

We just want to wrap the entire thing in a go block and then recursively transform all of the forms that may contain elipsis and splice them into the current list. You can find the full
code for the asynchronize [macro here](https://github.com/gilbertw1/cljs-asynchronize/blob/master/src/cljs_asynchronize/macros.clj).

## Conclusion

Well if your still here with me at this point then congratulations! (I think?) This post has turned out MUCH longer than I had anticipated. My goal was to create something to give beginners
all the tools they need to start writing macros, along with some advice and (slightly) advance use cases. I hope that this has been informative and has helped you at least in some small way.
Macros are awesome....go write some!

I personally am still very new to macros in clojure, if you find any inaccuracies or typos please let me know. Thanks for reading!