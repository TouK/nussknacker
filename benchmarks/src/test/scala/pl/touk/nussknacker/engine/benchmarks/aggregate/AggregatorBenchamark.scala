package pl.touk.nussknacker.engine.benchmarks.aggregate

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, State}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.Aggregator
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.{MathAggregator, SumAggregator}

import java.util.concurrent.TimeUnit

class AggregatorSetup(aggregator: Aggregator, outputType: TypingResult) {

  private val acc = aggregator.createAccumulator().asInstanceOf[aggregator.Aggregate]

  def addAndAlignType(n: Number): Unit = {
    val withElement = aggregator.addElement(n.asInstanceOf[aggregator.Element], acc)
    aggregator.alignToExpectedType(withElement, outputType)
  }

}

@State(Scope.Thread)
class AggregatorBenchamark {

  val sumAggregatorSetup = new AggregatorSetup(SumAggregator, Typed[Int])

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def sumAggregatorAdd(): Unit = {
    sumAggregatorSetup.addAndAlignType(1)
  }

}
