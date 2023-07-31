package pl.touk.nussknacker.ui.api.helpers.spel

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression.{spel, spelTemplate}
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.speltemplate

object SampleSpelTemplateProcess {

  import speltemplate.Implicits._

  val processName: ProcessName = ProcessName(this.getClass.getName)

  val process: CanonicalProcess = {
    ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#{1 != 2}")
      .to(endWithMessage("suffix", "message"))
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> spelTemplate("#test #{#input} #test \n#{\"abc\".toString + {1,2,3}.toString + \"abc\"}\n#test\n#{\"ab{}c\"}"))
      .emptySink("end" + idSuffix, "kafka-string", TopicParamName -> spelTemplate("end.topic"), SinkValueParamName -> spelTemplate("#output"))
  }
}