package pl.touk.nussknacker.restmodel

import io.circe.Json
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, NodeAdditionalFields}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}

class RestModelCodecsSpec extends FunSuite with Matchers {


  test("displayable process encode and decode") {
    val process = DisplayableProcess("", ProcessProperties(
      StreamMetaData(), ExceptionHandlerRef(List()),
      false,
      Some(ProcessAdditionalFields(Some("a"), Set(), Map("field1" -> "value1"))), Map()
    ), List(
      SubprocessInputDefinition("proc1", List(SubprocessParameter("param1", SubprocessClazzRef[String]))),
      CustomNode("id", Some("out1"), "typ1", List(Parameter("name1", Expression("spel", "11"))),
        Some(NodeAdditionalFields(Some("desc"))))
    ), List(
      Edge("from1", "to1", None)
    ), "")

    val encoded = CirceRestCodecs.displayableEncoder(process)

    encoded.hcursor.downField("edges").focus.flatMap(_.asArray) shouldBe Some(List(Json.obj(
      "from" -> Json.fromString("from1"),
      "to" -> Json.fromString("to1"),
      "edgeType" -> Json.Null
    )))

    CirceRestCodecs.displayableDecoder.decodeJson(encoded).right.toOption shouldBe Some(process)
  }

}
