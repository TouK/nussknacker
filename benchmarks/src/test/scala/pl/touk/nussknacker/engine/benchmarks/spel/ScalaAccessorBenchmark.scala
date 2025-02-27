package pl.touk.nussknacker.engine.benchmarks.spel

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class ScalaAccessorBenchmark {

  // This is basic SpEL expression, we can check e.g. if bytecode is properly generated for operators or scala accessors
  private val setup = new SpelBenchmarkSetup("#input.value + 15", Map("input" -> SampleCaseClass(15)))

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def benchmark(): AnyRef = {
    setup.test()
  }

}

final case class SampleCaseClass(value: Int)
