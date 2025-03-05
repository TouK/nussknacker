package pl.touk.nussknacker.engine.lite.components.requestresponse

import io.circe.parser.parse
import org.everit.json.schema.EmptySchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawValueParamName
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources.JsonSchemaRequestResponseSource

class RequestResponseTestWithParametersTest extends AnyFunSuite with Matchers {

  private val metaData: MetaData = MetaData("test1", RequestResponseMetaData(None))

  case class SimplifiedParam(name: String, typingResult: TypingResult, editor: Option[ParameterEditor])

  private def createSource(rawSchema: String) = {
    val schema = JsonSchemaBuilder.parseSchema(rawSchema)
    new JsonSchemaRequestResponseSource("", metaData, schema, EmptySchema.INSTANCE, NodeId(""))
  }

  val rawSchemaSimple =
    """
      |{
      | "type": "object",
      |   "properties": {
      |     "name": { "type": "string" },
      |     "age": { "type": "integer" }
      |   }
      |}
      |""".stripMargin

  test("should generate simple test parameters") {
    val source = createSource(rawSchemaSimple)
    val expectedParameters = List(
      SimplifiedParam(
        "name",
        Typed[String],
        Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
      ),
      SimplifiedParam("age", Typed[Long], None)
    )
    source.testParametersDefinition.map(p =>
      SimplifiedParam(p.name.value, p.typ, p.editor)
    ) should contain theSameElementsAs expectedParameters
  }

  val rawSchemaNested =
    """
      |{
      | "type": "object",
      |   "properties": {
      |     "address": {
      |       "type": "object",
      |       "properties": {
      |         "street": { "type": "string" },
      |         "number": { "type": "integer" }
      |       }
      |     },
      |     "additionalParams": { "type": "object" }
      |   }
      |}
      |""".stripMargin

  test("should generate nested test parameters") {
    val source = createSource(rawSchemaNested)
    val expectedParameters = List(
      SimplifiedParam(
        "address.street",
        Typed[String],
        Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
      ),
      SimplifiedParam("address.number", Typed[Long], None),
      SimplifiedParam(
        "additionalParams",
        Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown)),
        None
      )
    )
    source.testParametersDefinition.map(p =>
      SimplifiedParam(p.name.value, p.typ, p.editor)
    ) should contain theSameElementsAs expectedParameters
  }

  val combinedSchema =
    """
      |{
      |  "oneOf": [
      |    {
      |      "type": "object",
      |      "properties": {
      |        "result": {
      |          "type": "string"
      |        }
      |      },
      |      "additionalProperties": false
      |    },
      |    {
      |      "type": "object",
      |      "properties": {
      |        "errorCode": {
      |          "type": "integer"
      |        }
      |      },
      |      "additionalProperties": false,
      |    }
      |  ]
      |}
      |""".stripMargin

  test("should generate test parameters for combinedSchema") {
    val source = createSource(combinedSchema)
    val expectedParameters = List(
      SimplifiedParam(
        SinkRawValueParamName.value,
        Typed(
          Typed.record(
            Map("result" -> Typed[String]),
            Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[String]))
          ),
          Typed.record(
            Map("errorCode" -> Typed[Long]),
            Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[Long]))
          )
        ),
        None
      )
    )
    source.testParametersDefinition.map(p =>
      SimplifiedParam(p.name.value, p.typ, p.editor)
    ) should contain theSameElementsAs expectedParameters
  }

  test("should parse test parameters for combinedSchema") {
    val source     = createSource(combinedSchema)
    val parameters = Map(SinkRawValueParamName -> parse("{\"errorCode\": 20}").toOption.get)

    source.parametersToTestData(parameters) shouldBe TypedMap(Map("errorCode" -> 20L))
  }

}
