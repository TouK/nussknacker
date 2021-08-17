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

