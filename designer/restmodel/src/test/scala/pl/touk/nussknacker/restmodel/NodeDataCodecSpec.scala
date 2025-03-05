package pl.touk.nussknacker.restmodel

import io.circe.{parser, Decoder, Encoder, Json}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{CustomNode, FragmentInputDefinition, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class NodeDataCodecSpec extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("scenarioGraph encode and decode") {
    val scenarioGraph = ScenarioGraph(
      ProcessProperties.combineTypeSpecificProperties(
        StreamMetaData(),
        ProcessAdditionalFields(Some("a"), Map("field1" -> "value1"), StreamMetaData.typeName)
      ),
      List(
        FragmentInputDefinition("proc1", List(FragmentParameter(ParameterName("param1"), FragmentClazzRef[String]))),
        CustomNode(
          "id",
          Some("out1"),
          "typ1",
          List(NodeParameter(ParameterName("name1"), Expression.spel("11"))),
          Some(UserDefinedAdditionalNodeFields(Some("desc"), None))
        )
      ),
      List(
        Edge("from1", "to1", None)
      )
    )

    val encoded = Encoder[ScenarioGraph].apply(scenarioGraph)

    encoded.hcursor.downField("edges").focus.flatMap(_.asArray) shouldBe Some(
      List(
        Json.obj(
          "from"     -> Json.fromString("from1"),
          "to"       -> Json.fromString("to1"),
          "edgeType" -> Json.Null
        )
      )
    )

    Decoder[ScenarioGraph].decodeJson(encoded).toOption shouldBe Some(scenarioGraph)
  }

  test("decode scenarioGraph in legacy format with typeSpecificProperties") {
    val givenParallelism = 10
    val legacyJsonWithNoFields =
      s"""{
         |  "properties" : {
         |    "typeSpecificProperties" : {
         |      "parallelism" : $givenParallelism,
         |      "type" : "${StreamMetaData.typeName}"
         |    },
         |    "additionalFields" : null
         |  },
         |  "nodes" : [],
         |  "edges" : []
         |}""".stripMargin

    val parsedLegacy = parser.parse(legacyJsonWithNoFields).rightValue

    val decoded = Decoder[ScenarioGraph].decodeJson(parsedLegacy).rightValue
    decoded shouldEqual ScenarioGraph(
      ProcessProperties(
        ProcessAdditionalFields(
          None,
          Map(
            StreamMetaData.parallelismName            -> givenParallelism.toString,
            StreamMetaData.spillStateToDiskName       -> "true",
            StreamMetaData.useAsyncInterpretationName -> "",
            StreamMetaData.checkpointIntervalName     -> ""
          ),
          StreamMetaData.typeName
        )
      ),
      List.empty,
      List.empty
    )

  }

}
