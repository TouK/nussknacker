package pl.touk.nussknacker.engine.lite.components.requestresponse

import com.typesafe.config.ConfigFactory
import org.everit.json.schema.EmptySchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, RequestResponseMetaData}
import pl.touk.nussknacker.engine.compile.StubbedFragmentInputTestSource
import pl.touk.nussknacker.engine.definition.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources.JsonSchemaRequestResponseSource
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FixedExpressionValue => FragmentFixedExpressionValue}

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
      SimplifiedParam("name", Typed.apply[String], Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      SimplifiedParam("age", Typed.apply[Long], None)
    )
    source.testParametersDefinition.map(p => SimplifiedParam(p.name, p.typ, p.editor)) should contain theSameElementsAs expectedParameters
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
      SimplifiedParam("address.street", Typed.apply[String], Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      SimplifiedParam("address.number", Typed.apply[Long], None),
      SimplifiedParam("additionalParams", Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown)), None)
    )
    source.testParametersDefinition.map(p => SimplifiedParam(p.name, p.typ, p.editor)) should contain theSameElementsAs expectedParameters
  }

  test("should generate test parameters for fragment input definition") {
    val fragmentDefinitionExtractor = FragmentComponentDefinitionExtractor(ConfigFactory.empty, getClass.getClassLoader)
    val fragmentInputDefinition = FragmentInputDefinition("", List(
      FragmentParameter("name", FragmentClazzRef[String]),
      FragmentParameter("age", FragmentClazzRef[Long]),
    ))
    val stubbedSource = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameters: Seq[Parameter] = stubbedSource.createSource().testParametersDefinition
    val expectedParameters = List(
      SimplifiedParam("name", Typed.apply[String], Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      SimplifiedParam("age", Typed.apply[Long], None),
    )
    parameters.map(p => SimplifiedParam(p.name, p.typ, p.editor)) should contain theSameElementsAs expectedParameters
  }

  test("should generate complex test parameters for fragment input definition - 1") {
    val fragmentDefinitionExtractor = FragmentComponentDefinitionExtractor(ConfigFactory.empty, getClass.getClassLoader)
    val fragmentInputDefinition = FragmentInputDefinition("", List(
      FragmentParameter("name", FragmentClazzRef[String],
        required = true,
        fixedValueList = List(),
        defaultValue = Some(Expression.spel("'Tomasz'")),
        hintText = Some("some hint text")
        )
    ))
    val stubbedSource = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameter: Parameter = stubbedSource.createSource().testParametersDefinition.head

    parameter.name shouldBe "name"
    parameter.typ shouldBe Typed(classOf[String])
    parameter.editor shouldBe Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
    parameter.validators should contain theSameElementsAs List(MandatoryParameterValidator)
    parameter.defaultValue shouldBe Some(Expression.spel("'Tomasz'"))
    parameter.isOptional shouldBe false
    parameter.hintText shouldBe Some("some hint text")
  }

  test("should generate complex test parameters for fragment input definition - 2") {
    val fragmentDefinitionExtractor = FragmentComponentDefinitionExtractor(ConfigFactory.empty, getClass.getClassLoader)

    val fixedValueList = List(FragmentFixedExpressionValue("'aaa'", "aaa"), FragmentFixedExpressionValue("'bbb'", "bbb"))
    val fragmentInputDefinition = FragmentInputDefinition("", List(
      FragmentParameter("name", FragmentClazzRef[String],
        required = false,
        fixedValueList = fixedValueList,
        defaultValue = None,
        hintText = None
      )
    ))
    val stubbedSource = new StubbedFragmentInputTestSource(fragmentInputDefinition, fragmentDefinitionExtractor)
    val parameter: Parameter = stubbedSource.createSource().testParametersDefinition.head

    parameter.name shouldBe "name"
    parameter.typ shouldBe Typed(classOf[String])
    parameter.editor shouldBe Some(DualParameterEditor(FixedValuesParameterEditor(fixedValueList.map(v => FixedExpressionValue(v.expression, v.label))), DualEditorMode.SIMPLE))
    parameter.validators should contain theSameElementsAs List()
    parameter.defaultValue shouldBe Some(Expression("spel", ""))
    parameter.isOptional shouldBe true
    parameter.hintText shouldBe None
  }

}
