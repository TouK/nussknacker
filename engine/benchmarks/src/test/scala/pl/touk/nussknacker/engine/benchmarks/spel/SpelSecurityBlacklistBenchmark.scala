/**
  * BEFORE ADDING BLACKLISTED BLOCKING IN RUNTIME
[info] # Warmup Iteration   1: 0.118 us/op
[info] # Warmup Iteration   2: 0.119 us/op
[info] # Warmup Iteration   3: 0.139 us/op
[info] Iteration   1: 0.138 us/op
[info] Iteration   2: 0.139 us/op
[info] Iteration   3: 0.149 us/op
[info] Iteration   4: 0.138 us/op
[info] Iteration   5: 0.138 us/op
[info] Iteration   6: 0.138 us/op
[info] Iteration   7: 0.138 us/op
[info] Iteration   8: 0.138 us/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.spel.SpelSecurityBlacklistBenchmark.benchmark":
[info]   0.140 ±(99.9%) 0.007 us/op [Average]
[info]   (min, avg, max) = (0.138, 0.140, 0.149), stdev = 0.004
[info]   CI (99.9%): [0.132, 0.147] (assumes normal distribution)
[info] # Run complete. Total time: 00:01:51
[info] Benchmark                                 Mode  Cnt  Score   Error  Units
[info] SpelSecurityBlacklistBenchmark.benchmark  avgt    8  0.140 ± 0.007  us/op
[success] Total time: 113 s (01:53), completed Aug 20, 2021, 9:40:39 AM
  */

/**
  * AFTER ADDING BLACKLISTED BLOCKING IN RUNTIME
[info] # Warmup Iteration   1: 0.118 us/op
[info] # Warmup Iteration   2: 0.140 us/op
[info] # Warmup Iteration   3: 0.151 us/op
[info] Iteration   1: 0.140 us/op
[info] Iteration   2: 0.140 us/op
[info] Iteration   3: 0.141 us/op
[info] Iteration   4: 0.140 us/op
[info] Iteration   5: 0.140 us/op
[info] Iteration   6: 0.152 us/op
[info] Iteration   7: 0.144 us/op
[info] Iteration   8: 0.143 us/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.spel.SpelSecurityBlacklistBenchmark.benchmark":
[info]   0.143 ±(99.9%) 0.008 us/op [Average]
[info]   (min, avg, max) = (0.140, 0.143, 0.152), stdev = 0.004
[info]   CI (99.9%): [0.135, 0.151] (assumes normal distribution)
[info] # Run complete. Total time: 00:01:51
[info] Benchmark                                 Mode  Cnt  Score   Error  Units
[info] SpelSecurityBlacklistBenchmark.benchmark  avgt    8  0.143 ± 0.008  us/op
[success] Total time: 113 s (01:53), completed Aug 20, 2021, 9:43:22 AM
[IJ]
  */


package pl.touk.nussknacker.engine.benchmarks.spel

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class SpelSecurityBlacklistBenchmark {

  private val setup = new SpelSecurityBenchmarkSetup("T(java.lang.String).valueOf(1L)", Map.empty)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def benchmark(): AnyRef = {
    setup.test()
  }
}



