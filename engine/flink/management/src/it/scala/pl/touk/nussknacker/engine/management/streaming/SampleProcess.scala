package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode

object SampleProcess {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  def prepareProcess(name: ProcessName, parallelism: Option[Int] = None): CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(name.value)
    parallelism
      .map(baseProcessBuilder.parallelism)
      .getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true".spel, endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("endSend", "sendSms", "Value" -> "'message'".spel)
  }

  def kafkaProcess(name: ProcessName, topic: String): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("startProcess", "real-kafka", "Topic" -> s"'$topic'".spel)
      .emptySink("end", "kafka-string", "Topic" -> s"'output-$name'".spel, "Value" -> "#input".spel)
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'".spel)
      .emptySink("end" + idSuffix, "monitor")
  }

}
