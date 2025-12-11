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

  def prepareProcessWithNoWatermarkStrategyForTest(name: ProcessName): CanonicalProcess = {
    ScenarioBuilder
      .streaming(name.value)
      .source("startProcess", "kafka-transaction-no-test-timestamp-assigner")
      .customNodeNoOutput("timestampReadingNode", "timestampReader")
      .emptySink("endSend", "sendSms", "Value" -> "'message'".spel)
  }

  def prepareProcessWithEventGeneratorSource(name: ProcessName, parallelism: Option[Int] = None): CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(name.value)
    parallelism
      .map(baseProcessBuilder.parallelism)
      .getOrElse(baseProcessBuilder)
      .source(
        "start",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
        "count"    -> "1".spel,
        "value"    -> s"'abrakadabra'".spel
      )
      .emptySink("endSend", "sendSms", "Value" -> "'message'".spel)
  }

  private def endWithMessage(idSuffix: String, message: String): Option[SubsequentNode] = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'".spel)
      .emptySink("end" + idSuffix, "monitor")
  }

}
