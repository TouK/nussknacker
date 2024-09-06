# Microbenchmarks
Benchmarks in this module are suitable for things like:
- performance of SpEL evaluation
- handling Context operations
and probably not for more end to end scenarios - like testing Flink process

For some benchmarks sample values for 4-core machine are included. They **should not** be considered
as proper reference, since results will vary a lot between machines/runs. However, if after some changes
the results are like 10 times worse it's probably worth having a closer look ;)

## Docs:
https://github.com/sbt/sbt-jmh

## Running in sbt:
```bash
benchmarks/Jmh/run -i 8 -wi 3 -f1 -t2 .*SampleSpelBenchmark.*
benchmarks/Jmh/run -i 8 -wi 3 -f1 -t2 -prof jfr:stackDepth=512 .*SampleSpelBenchmark.*
```

# E2E benchmarks
Benchmarks based on the docker installation example environment. 
The docker from sources is built and published in local registry and then the benchmark code is run.

## Running in sbt:
```bash
bash -c "export NUSSKNACKER_SCALA_VERSION=2.12 && sbt \"benchmarks/test:runMain pl.touk.nussknacker.engine.benchmarks.e2e.FlinkSteamingScenarioBenchmark 10000000\""
```

> :warning: **Warning:** At the moment they support only Scala 2.12-based Nu-stack
