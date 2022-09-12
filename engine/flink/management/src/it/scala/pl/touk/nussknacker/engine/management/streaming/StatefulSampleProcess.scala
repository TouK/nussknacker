package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel

object StatefulSampleProcess {

  import spel.Implicits._


  def prepareProcess(id: String): CanonicalProcess = {

   ScenarioBuilder
      .streaming(id)
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "stateful", "groupBy" -> "#input")
        .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "#stateVar")
  }

  def prepareProcessStringWithStringState(id: String): CanonicalProcess = {

   ScenarioBuilder
      .streaming(id)
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformer")
        .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "#stateVar")
  }

  def processWithAggregator(id: String, aggregatorExpression: String): CanonicalProcess = ScenarioBuilder
    .streaming(id)
    .source("state", "oneSource")
    .customNode("transform", "aggregate", "aggregate-sliding",
      "groupBy" -> "'test'",
      "aggregator" -> aggregatorExpression,
      "aggregateBy" -> "1",
      "windowLength" -> "T(java.time.Duration).parse('PT1H')",
      "emitWhenEventLeft" -> "false"
    )
    // Add enricher to force creating async operator which buffers elements emitted by aggregation. These elements can be incompatible.
    .enricher("enricher", "output", "paramService", "param" -> "'a'")
    .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "'test'")

  def prepareProcessWithLongState(id: String): CanonicalProcess = {

   ScenarioBuilder
      .streaming(id)
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformerLongValue")
        .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "#stateVar")
  }
}
