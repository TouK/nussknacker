package pl.touk.nussknacker.engine.benchmarks.spel

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, State, Threads}
import pl.touk.nussknacker.engine.api.Context

import java.util.UUID
import java.util.concurrent.TimeUnit

/**
  * results:
  * - UUIDBenchmark.secureUUIDTest    avgt   25  9.873 ± 0.467  us/op
  * - UUIDBenchmark.unsecureUUIDTest  avgt   25  6.227 ± 0.130  us/op
  */
@State(Scope.Benchmark)
class UUIDBenchmark {

  @Threads(10)
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def unsecureUUIDTest(): AnyRef = {
    Context.withInitialId
  }

  @Threads(10)
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def secureUUIDTest(): AnyRef = {
    Context(UUID.randomUUID().toString)
  }

}
