package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.api.graph.EspProcess
import pl.touk.nussknacker.engine.api.graph.expression.Expression
import pl.touk.nussknacker.engine.spel

object StatefulSampleProcess {

  import spel.Implicits._


  def prepareProcess(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
     .exceptionHandler()
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "stateful", "groupBy" -> "#input")
        .sink("end", "#stateVar": Expression, "kafka-string", "topic" -> s"'output-$id'")
  }

  def prepareProcessStringWithStringState(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
     .exceptionHandler()
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformer")
        .sink("end", "#stateVar", "kafka-string", "topic" -> s"'output-$id'")
  }

  def processWithMapAggegator(id: String, aggegatorExpression: String) =     EspProcessBuilder
    .id(id)
    .exceptionHandler()
    .source("state", "oneSource")
    .customNode("transform", "aggregate", "aggregate",
      "groupBy" -> "'test'",
      "aggregator" -> s"#AGG.map({x: $aggegatorExpression})",
      "aggregateBy" -> "{ x: 1 }",
      "windowLength" -> "T(java.time.Duration).parse('PT1H')",
      "emitWhenEventLeft" -> "false"
    )
    .sink("end", "'test'", "kafka-string", "topic" -> s"'output-$id'")

  def prepareProcessWithLongState(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
     .exceptionHandler()
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformerLongValue")
        .sink("end", "#stateVar", "kafka-string", "topic" -> s"'output-$id'")
  }
}
