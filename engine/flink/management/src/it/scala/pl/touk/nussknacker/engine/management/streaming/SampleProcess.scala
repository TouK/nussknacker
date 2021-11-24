package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String, parallelism: Option[Int] = None) : EspProcess = {
    val baseProcessBuilder = EspProcessBuilder.id(id)
    parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("endSend", "sendSms", "value" -> "'message'")
  }

  def kafkaProcess(id: String, topic: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .source("startProcess", "real-kafka", "topic" -> s"'$topic'")
      .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "#input")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "monitor")
  }

}
