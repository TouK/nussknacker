package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  val processName: ProcessName = ProcessName(this.getClass.getName)

  val process: CanonicalProcess = {
    ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#input != null")
      .to(endWithMessage("suffix", "message"))
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "#output")
  }

}
