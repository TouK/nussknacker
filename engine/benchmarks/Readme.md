Microbenchmarks
---------------
Benchmarks in this module are suitable for things like:
- performance of SpEL evaluation
- handling Context operations
and probably not for more end to end scenarios - like testing Flink process

Docs:
-----
https://github.com/ktoso/sbt-jmh

Running in sbt:
---------------
benchmarks/jmh:run -i 8 -wi 3 -f1 -t2 .*SampleSpelBenchmark.*

