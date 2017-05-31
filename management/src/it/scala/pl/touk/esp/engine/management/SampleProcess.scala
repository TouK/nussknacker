package pl.touk.esp.engine.management

import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.{Case, Node, SubsequentNode}
import pl.touk.esp.engine.graph.variable.Field
import pl.touk.esp.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler("param1" -> "val1")
      .source("startProcess", "kafka-transaction")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .sink("endSend", "sendSms")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .sink("end" + idSuffix, "monitor")
  }

}
