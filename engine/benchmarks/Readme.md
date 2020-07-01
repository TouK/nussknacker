Microbenchmarks
---------------
Benchmarks in this module are suitable for things like:
- performance of SpEL evaluation
- handling Context operations
and probably not for more end to end scenarios - like testing Flink process

For some benchmarks sample values for 4-core machine are included. They **should not** be considered
as proper reference, since results will vary a lot between machines/runs. However, if after some changes
the results are like 10 times worse it's probably worth having a closer look ;)


Docs:
-----
https://github.com/ktoso/sbt-jmh

Running in sbt:
---------------
benchmarks/jmh:run -i 8 -wi 3 -f1 -t2 .*SampleSpelBenchmark.*

