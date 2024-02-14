package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel

object StatefulSampleProcess {

  import spel.Implicits._

  def prepareProcess(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "stateful", "groupBy" -> "#input")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'", "Value" -> "#stateVar")
  }

  def prepareProcessStringWithStringState(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "constantStateTransformer")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'", "Value" -> "#stateVar")
  }

  def processWithAggregator(name: ProcessName, aggregatorExpression: String): CanonicalProcess = ScenarioBuilder
    .streaming(name.value)
    .source("state", "oneSource")
    .customNode(
      "transform",
      "aggregate",
      "aggregate-sliding",
      "groupBy"           -> "'test'",
      "aggregator"        -> aggregatorExpression,
      "aggregateBy"       -> "1",
      "windowLength"      -> "T(java.time.Duration).parse('PT1H')",
      "emitWhenEventLeft" -> "false"
    )
    // Add enricher to force creating async operator which buffers elements emitted by aggregation. These elements can be incompatible.
    .enricher("enricher", "output", "paramService", "param" -> "'a'")
    .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'", "Value" -> "'test'")

  def prepareProcessWithLongState(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "constantStateTransformerLongValue")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'", "Value" -> "#stateVar")
  }

}
