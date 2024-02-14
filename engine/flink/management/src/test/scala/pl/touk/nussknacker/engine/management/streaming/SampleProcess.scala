package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(name: ProcessName, parallelism: Option[Int] = None): CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(name.value)
    parallelism
      .map(baseProcessBuilder.parallelism)
      .getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("endSend", "sendSms", "Value" -> "'message'")
  }

  def kafkaProcess(name: ProcessName, topic: String): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("startProcess", "real-kafka", "Topic" -> s"'$topic'")
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'", "Value" -> "#input")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "monitor")
  }

}
