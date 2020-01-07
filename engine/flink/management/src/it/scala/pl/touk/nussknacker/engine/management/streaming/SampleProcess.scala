package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler()
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("endSend", "sendSms")
  }

  def kafkaProcess(id: String, topic: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler()
      .source("startProcess", "real-kafka", "topic" -> s"'$topic'")
      .sink("end", "#input", "kafka-string", "topic" -> s"'output-$id'")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "monitor")
  }

}
