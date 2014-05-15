blog-gen
========

A hand rolled static (blog) site generator writting in clojure using [stasis](https://github.com/magnars/stasis). Inspired very heavily by [this blog post](http://cjohansen.no/building-static-sites-in-clojure-with-stasis).

This project powers http://bryangilbert.com and http://bryan.codes

Running
-------

Local Server

    $ lein ring server

Build Site (exports to dist/)

    $ lein build-site