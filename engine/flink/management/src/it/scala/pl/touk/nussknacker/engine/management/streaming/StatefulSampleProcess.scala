package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

object StatefulSampleProcess {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  def prepareProcess(name: ProcessName, parallelism: Int = 1): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .parallelism(parallelism)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "stateful", "groupBy" -> "#input".spel)
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'".spel, "Value" -> "#stateVar".spel)
  }

  def prepareProcessStringWithStringState(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "constantStateTransformer")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'".spel, "Value" -> "#stateVar".spel)
  }

  def processWithAggregator(name: ProcessName, aggregatorExpression: String): CanonicalProcess = ScenarioBuilder
    .streaming(name.value)
    .source("state", "oneSource")
    .customNode(
      "transform",
      "aggregate",
      "aggregate-sliding",
      "groupBy"           -> "'test'".spel,
      "aggregator"        -> aggregatorExpression.spel,
      "aggregateBy"       -> "1".spel,
      "windowLength"      -> "T(java.time.Duration).parse('PT1H')".spel,
      "emitWhenEventLeft" -> "false".spel
    )
    // Add enricher to force creating async operator which buffers elements emitted by aggregation. These elements can be incompatible.
    .enricher("enricher", "output", "paramService", "param" -> "'a'".spel)
    .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'".spel, "Value" -> "'test'".spel)

  def prepareProcessWithLongState(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("state", "oneSource")
      .customNode("stateful", "stateVar", "constantStateTransformerLongValue")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'".spel, "Value" -> "#stateVar".spel)
  }

}
