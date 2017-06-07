package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{Case, Node, SubsequentNode}
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .sink("endSend", "sendSms")
  }

  def kafkaProcess(id: String, topic: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("startProcess", "real-kafka", "topic" -> topic)
      .sink("end", "#input", "kafka-string", "topic" -> s"output-$id")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .sink("end" + idSuffix, "monitor")
  }

}
