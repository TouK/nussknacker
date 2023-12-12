package pl.touk.nussknacker.engine.lite.components.requestresponse

import com.typesafe.config.ConfigFactory
import io.circe.parser.parse
import org.everit.json.schema.EmptySchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, RequestResponseMetaData}
import pl.touk.nussknacker.engine.compile.StubbedFragmentInputTestSource
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue => FragmentFixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawValueParamName
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources.JsonSchemaRequestResponseSource
import pl.touk.nussknacker.engine.testing.LocalModelData

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
      SimplifiedParam(p.name, p.typ, p.editor)
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
      SimplifiedParam(p.name, p.typ, p.editor)
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
        SinkRawValueParamName,
        Typed(
          TypedObjectTypingResult(
            Map("result" -> Typed[String]),
            Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[String]))
          ),
          TypedObjectTypingResult(
            Map("errorCode" -> Typed[Long]),
            Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[Long]))
          )
        ),
        None
      )
    )
    source.testParametersDefinition.map(p =>
      SimplifiedParam(p.name, p.typ, p.editor)
    ) should contain theSameElementsAs expectedParameters
  }

  test("should parse test parameters for combinedSchema") {
    val source     = createSource(combinedSchema)
    val parameters = Map(SinkRawValueParamName -> parse("{\"errorCode\": 20}").toOption.get)

    source.parametersToTestData(parameters) shouldBe TypedMap(Map("errorCode" -> 20L))
  }

  test("should generate test parameters for fragment input definition") {
    val fragmentDefinitionExtractor =
      FragmentComponentDefinitionExtractor(
        LocalModelData(ConfigFactory.empty, List.empty)
      )
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter("name", FragmentClazzRef[String]),
        FragmentParameter("age", FragmentClazzRef[Long]),
      )
    )
    val stubbedSource = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameters: Seq[Parameter] = stubbedSource.createSource().testParametersDefinition
    val expectedParameters = List(
      SimplifiedParam(
        "name",
        Typed[String],
        Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
      ),
      SimplifiedParam("age", Typed[Long], None),
    )
    parameters.map(p => SimplifiedParam(p.name, p.typ, p.editor)) should contain theSameElementsAs expectedParameters
  }

  test("should generate parameters for expanded fragment input definition without fixed values") {
    val fragmentDefinitionExtractor =
      FragmentComponentDefinitionExtractor(
        LocalModelData(ConfigFactory.empty, List.empty)
      )
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter(
          "name",
          FragmentClazzRef[String],
          required = true,
          initialValue = Some(FragmentFixedExpressionValue("'Tomasz'", "Tomasz")),
          hintText = Some("some hint text"),
          valueEditor = None
        )
      )
    )
    val stubbedSource        = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameter: Parameter = stubbedSource.createSource().testParametersDefinition.head

    parameter.name shouldBe "name"
    parameter.typ shouldBe Typed[String]
    parameter.editor shouldBe Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
    parameter.validators should contain theSameElementsAs List(MandatoryParameterValidator)
    parameter.defaultValue shouldBe Some(Expression.spel("'Tomasz'"))
    parameter.hintText shouldBe Some("some hint text")
  }

  test("should generate complex parameters for expanded fragment input definition with fixed values") {
    val fragmentDefinitionExtractor =
      FragmentComponentDefinitionExtractor(
        LocalModelData(ConfigFactory.empty, List.empty)
      )

    val fixedValuesList =
      List(FragmentFixedExpressionValue("'aaa'", "aaa"), FragmentFixedExpressionValue("'bbb'", "bbb"))
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter(
          "name",
          FragmentClazzRef[String],
          required = false,
          initialValue = None,
          hintText = None,
          valueEditor = Some(
            ValueInputWithFixedValuesProvided(
              allowOtherValue = true,
              fixedValuesList = fixedValuesList
            )
          )
        )
      )
    )

    val stubbedSource        = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameter: Parameter = stubbedSource.createSource().testParametersDefinition.head

    parameter.name shouldBe "name"
    parameter.typ shouldBe Typed[String]
    parameter.editor shouldBe Some(
      DualParameterEditor(
        FixedValuesParameterEditor(
          FixedExpressionValue("", "") +: fixedValuesList.map(v => FixedExpressionValue(v.expression, v.label))
        ),
        DualEditorMode.SIMPLE
      )
    )
    parameter.validators should contain theSameElementsAs List()
    parameter.defaultValue shouldBe Some(Expression("spel", ""))
    parameter.hintText shouldBe None
  }

}
