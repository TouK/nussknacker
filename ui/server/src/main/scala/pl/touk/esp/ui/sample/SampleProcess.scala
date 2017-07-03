package pl.touk.esp.ui.sample

import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.SubsequentNode
import pl.touk.esp.engine.spel

object SampleProcess {

  import spel.Implicits._

  val process: EspProcess = {
    EspProcessBuilder
      .id("sampleProcess")
      .parallelism(1)
      .exceptionHandler("param1" -> "ala")
      .source("startProcess", "csv-source")
      .filter("input", "#input != 'ala'")
      .to(endWithMessage("suffix", "message"))
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .sink("end" + idSuffix, "kafka-string", "topic" -> "end.topic")
  }

}
