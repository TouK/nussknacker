package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.CustomNode
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentEntry, ProcessHistoryEntry}

class RestModelCodecsSpec extends FunSuite with RestModelCodecs {


  test("displayable process encode") {
    val process = DisplayableProcess("", ProcessProperties(
      StreamMetaData(), ExceptionHandlerRef(List()),
      false,
      Some(ProcessAdditionalFields(Some("a"), Set(), Map("field1" -> "value1"))), Map()
    ), List(
      CustomNode("id", Some("out1"), "typ1", List(Parameter("name1", Expression("spel", "11"))),
        Some(NodeAdditionalFields(Some("desc"))))
    ), List(

    ), "")

    displayableProcessCodec.encode(process)
  }

  test("process history encode") {

    processHistoryEncode.encode(ProcessHistoryEntry("id", "name", 10, LocalDateTime.now(), "", List(
      DeploymentEntry(12, "env", LocalDateTime.now(), "user", Map("key" -> "value"))
    )))
  }

}
