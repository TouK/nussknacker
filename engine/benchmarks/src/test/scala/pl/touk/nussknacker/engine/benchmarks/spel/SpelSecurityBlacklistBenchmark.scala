package pl.touk.nussknacker.engine.benchmarks.spel

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit


@State(Scope.Thread)
class SpelSecurityBlacklistBenchmark {

  private val setup = new SpelBenchmarkSetup("T(java.math.BigInteger).valueOf(1L)", Map.empty)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def benchmark(): AnyRef = {
    setup.test()
  }
}



/**
  * BEFORE ADDING BLACKLISTED BLOCKING IN RUNTIME
[info] # Warmup Iteration   1: 0.140 us/op
[info] # Warmup Iteration   2: 0.160 us/op
[info] # Warmup Iteration   3: 0.174 us/op
[info] Iteration   1: 0.165 us/op
[info] Iteration   2: 0.163 us/op
[info] Iteration   3: 0.157 us/op
[info] Iteration   4: 0.157 us/op
[info] Iteration   5: 0.159 us/op
[info] Iteration   6: 0.172 us/op
[info] Iteration   7: 0.166 us/op
[info] Iteration   8: 0.158 us/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.spel.SpelSecurityBlacklistBenchmark.benchmark":
[info]   0.162 ±(99.9%) 0.010 us/op [Average]
[info]   (min, avg, max) = (0.157, 0.162, 0.172), stdev = 0.005
[info]   CI (99.9%): [0.152, 0.172] (assumes normal distribution)
[info] # Run complete. Total time: 00:01:51
[info] Benchmark                                 Mode  Cnt  Score   Error  Units
[info] SpelSecurityBlacklistBenchmark.benchmark  avgt    8  0.162 ± 0.010  us/op
  */

/**
  * AFTER ADDING BLACKLISTED BLOCKING IN RUNTIME
[info] # Warmup Iteration   1: 0.165 us/op
[info] # Warmup Iteration   2: 0.166 us/op
[info] # Warmup Iteration   3: 0.160 us/op
[info] Iteration   1: 0.172 us/op
[info] Iteration   2: 0.191 us/op
[info] Iteration   3: 0.229 us/op
[info] Iteration   4: 0.159 us/op
[info] Iteration   5: 0.176 us/op
[info] Iteration   6: 0.170 us/op
[info] Iteration   7: 0.156 us/op
[info] Iteration   8: 0.156 us/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.spel.SpelSecurityBlacklistBenchmark.benchmark":
[info]   0.176 ±(99.9%) 0.047 us/op [Average]
[info]   (min, avg, max) = (0.156, 0.176, 0.229), stdev = 0.024
[info]   CI (99.9%): [0.129, 0.223] (assumes normal distribution)
[info] # Run complete. Total time: 00:01:51
[info] Benchmark                                 Mode  Cnt  Score   Error  Units
[info] SpelSecurityBlacklistBenchmark.benchmark  avgt    8  0.176 ± 0.047  us/op
  */


