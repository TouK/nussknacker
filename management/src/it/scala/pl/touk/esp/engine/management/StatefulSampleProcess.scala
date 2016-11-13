package pl.touk.esp.engine.management

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph._
import pl.touk.esp.engine.spel
import scala.concurrent.duration._

object StatefulSampleProcess {

  import spel.Implicits._


  def prepareProcess(id: String): EspProcess = {

   EspProcessBuilder
      .id(id)
      .exceptionHandler()
      .source("state", "oneSource")
          .customNode("stateful", "input", "stateful", "keyBy" -> "#input")
        .sink("end", "#input", "kafka-string", "topic" -> s"output-$id")
  }
}
