---
title : Integrating performance benchmarks into sbt project using Google Caliper
tags : [scala, sbt, google caliper, performance, benchmarking]
---

During my time working with scala one of the biggest things that I've learned is that we must be very mindful of 
exactly what our code is doing under the surface and what the performance implications of that behavior is. The scala
compiler does many amazing things for you, but in many cases it can make it very difficult to reason about the 
performance characteristics of your code. This is why as a scala developer, one of your biggest friends can be 
micro benchmarks. Micro benchmarks can tell you definitively how your code is performing in particular scenarios.

## Google Caliper

Google caliper is an excellent micro benchmarking utility written by the guys at google