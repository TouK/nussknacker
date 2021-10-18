package pl.touk.nussknacker.restmodel

import io.circe.{Decoder, Encoder, Json}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, SubprocessInputDefinition, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}

class NodeDataCodecSpec extends FunSuite with Matchers {


  test("displayable process encode and decode") {
    val process = DisplayableProcess("", ProcessProperties(
      StreamMetaData(), ExceptionHandlerRef(List()),
      Some(ProcessAdditionalFields(Some("a"), Map("field1" -> "value1"))), Map()
    ), List(
      SubprocessInputDefinition("proc1", List(SubprocessParameter("param1", SubprocessClazzRef[String]))),
      CustomNode("id", Some("out1"), "typ1", List(Parameter("name1", Expression("spel", "11"))),
        Some(UserDefinedAdditionalNodeFields(Some("desc"), None)))
    ), List(
      Edge("from1", "to1", None)
    ), "")

    val encoded = Encoder[DisplayableProcess].apply(process)

    encoded.hcursor.downField("edges").focus.flatMap(_.asArray) shouldBe Some(List(Json.obj(
      "from" -> Json.fromString("from1"),
      "to" -> Json.fromString("to1"),
      "edgeType" -> Json.Null
    )))

    Decoder[DisplayableProcess].decodeJson(encoded).right.toOption shouldBe Some(process)
  }

}
