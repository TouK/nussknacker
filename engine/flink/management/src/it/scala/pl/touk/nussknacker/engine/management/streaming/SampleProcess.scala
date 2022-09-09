package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String, parallelism: Option[Int] = None) : CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(id)
    parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("endSend", "sendSms", "value" -> "'message'")
  }

  def kafkaProcess(id: String, topic: String) : CanonicalProcess = {
    ScenarioBuilder
      .streaming(id)
      .source("startProcess", "real-kafka", "topic" -> s"'$topic'")
      .emptySink("end", "kafka-string", "topic" -> s"'output-$id'", "value" -> "#input")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "monitor")
  }

}
