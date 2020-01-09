package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  val process: EspProcess = {
    EspProcessBuilder
      .id("sampleProcess")
      .parallelism(1)
      .exceptionHandler()
      .source("startProcess", "csv-source")
      .filter("input", "#input != null")
      .to(endWithMessage("suffix", "message"))
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "kafka-string", "topic" -> "'end.topic'")
  }

}
