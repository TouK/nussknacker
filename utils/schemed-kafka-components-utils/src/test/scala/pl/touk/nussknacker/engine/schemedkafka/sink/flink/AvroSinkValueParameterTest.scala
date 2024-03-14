package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.apache.avro.SchemaBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schema.AvroSchemaBasedParameter
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedRecordParameter, SingleSchemaBasedParameter}

class AvroSchemaBasedParameterTest extends AnyFunSuite with Matchers {
  private implicit val nodeId: NodeId = NodeId("")

  test("typing result to AvroSinkRecordParameter") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
      .name("a")
      .`type`()
      .stringType()
      .noDefault()
      .name("b")
      .`type`()
      .record("B")
      .fields()
      .name("c")
      .`type`()
      .longType()
      .noDefault()
      .endRecord()
      .noDefault()
      .name("c")
      .`type`()
      .stringType()
      .stringDefault("c-field-default")
      .name("d")
      .`type`()
      .longType()
      .longDefault(42)
      .name("e")
      .`type`()
      .unionOf()
      .nullType()
      .and()
      .longType()
      .endUnion()
      .nullDefault()
      .endRecord()

    val result = AvroSchemaBasedParameter(recordSchema, Set.empty)
      .valueOr(e => fail(e.toString))
      .asInstanceOf[SchemaBasedRecordParameter]
    StandardParameterEnrichment.enrichParameterDefinitions(
      result.toParameters,
      Map.empty
    ) shouldBe List(
      Parameter(name = ParameterName("a"), typ = typing.Typed[String]).copy(
        isLazyParameter = true,
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
        defaultValue = Some(Expression.spel("''"))
      ),
      Parameter(name = ParameterName("b.c"), typ = typing.Typed[Long])
        .copy(isLazyParameter = true, defaultValue = Some(Expression.spel("0"))),
      Parameter(name = ParameterName("c"), typ = typing.Typed[String]).copy(
        isLazyParameter = true,
        defaultValue = Some(Expression.spel("'c-field-default'")),
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
      ),
      Parameter(name = ParameterName("d"), typ = typing.Typed[Long])
        .copy(isLazyParameter = true, defaultValue = Some(Expression.spel("42L"))),
      Parameter(name = ParameterName("e"), typ = typing.Typed[Long])
        .copy(isLazyParameter = true, defaultValue = Some(Expression.spel("null")), validators = Nil)
    )
  }

  test("typing result to AvroSinkPrimitiveValueParameter") {
    val longSchema = SchemaBuilder.builder().longType()
    val result = AvroSchemaBasedParameter(longSchema, Set.empty)
      .valueOr(e => fail(e.toString))
      .asInstanceOf[SingleSchemaBasedParameter]
    StandardParameterEnrichment.enrichParameterDefinitions(
      result.toParameters,
      Map.empty
    ) shouldBe List(
      Parameter(name = sinkValueParamName, typ = typing.Typed[Long])
        .copy(isLazyParameter = true, defaultValue = Some(Expression.spel("0")))
    )
  }

  test("typed object with restricted field names") {
    val restrictedNames: Set[ParameterName] =
      Set(schemaVersionParamName, sinkKeyParamName, sinkValidationModeParamName, topicParamName)
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
      .name(sinkKeyParamName.value)
      .`type`()
      .stringType()
      .noDefault()
      .name("b")
      .`type`()
      .longType()
      .noDefault()
      .endRecord()
    val result = AvroSchemaBasedParameter(recordSchema, restrictedNames)
    result shouldBe Invalid(
      NonEmptyList.one(
        CustomNodeError(
          nodeId.id,
          s"""Record field name is restricted. Restricted names are Schema version, Key, Value validation mode, Topic""",
          None
        )
      )
    )
  }

}
