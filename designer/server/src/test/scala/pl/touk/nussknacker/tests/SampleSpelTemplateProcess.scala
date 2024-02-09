package pl.touk.nussknacker.tests

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression.spelTemplate
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}

object SampleSpelTemplateProcess {

  val processName: ProcessName = ProcessName(this.getClass.getName)

  val process: CanonicalProcess = {
    ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .to(endWithMessage)
  }

  private def endWithMessage: SubsequentNode = {
    val idSuffix   = "suffix"
    val endMessage = "#test #{#input} #test \n#{\"abc\".toString + {1,2,3}.toString + \"abc\"}\n#test\n#{\"ab{}c\"}"

    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> spelTemplate(endMessage))
      .emptySink(
        "end" + idSuffix,
        "kafka-string",
        TopicParamName     -> spelTemplate("end.topic"),
        SinkValueParamName -> spelTemplate("#output")
      )
  }

}
