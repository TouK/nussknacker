package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph._
import pl.touk.nussknacker.engine.spel
import scala.concurrent.duration._

object StatefulSampleProcess {

  import spel.Implicits._


  def prepareProcess(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "stateful", "keyBy" -> "#input")
        .sink("end", "#stateVar", "kafka-string", "topic" -> s"output-$id")
  }

  def prepareProcessStringWithStringState(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformer")
        .sink("end", "#stateVar", "kafka-string", "topic" -> s"output-$id")
  }

  def prepareProcessWithLongState(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("state", "oneSource")
        .customNode("stateful", "stateVar", "constantStateTransformerLongValue")
        .sink("end", "#stateVar", "kafka-string", "topic" -> s"output-$id")
  }
}
