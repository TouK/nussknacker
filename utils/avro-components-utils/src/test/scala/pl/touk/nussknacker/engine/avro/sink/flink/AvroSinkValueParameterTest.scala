package pl.touk.nussknacker.engine.avro.sink.flink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.apache.avro.SchemaBuilder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SinkKeyParamName, SinkValueParamName}
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.definition.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter}

class AvroSinkValueParameterTest extends FunSuite with Matchers {
  private implicit val nodeId: NodeId = NodeId("")

  test("typing result to AvroSinkRecordParameter") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
        .name("a").`type`().stringType().noDefault()
        .name("b").`type`()
          .record("B")
          .fields()
            .name("c").`type`().longType().noDefault()
          .endRecord().noDefault()
        .name("c").`type`().stringType().stringDefault("c-field-default")
        .name("d").`type`().longType().longDefault(42)
        .name("e").`type`().unionOf().nullType().and().longType().endUnion().nullDefault()
      .endRecord()

    val result = AvroSinkValueParameter(recordSchema).valueOr(e => fail(e.toString)).asInstanceOf[SinkRecordParameter]
    StandardParameterEnrichment.enrichParameterDefinitions(result.toParameters, SingleComponentConfig.zero) shouldBe List(
      Parameter(name = "a", typ = typing.Typed[String]).copy(isLazyParameter = true, editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)), defaultValue = Some("''")),
      Parameter(name = "b.c", typ = typing.Typed[Long]).copy(isLazyParameter = true, defaultValue = Some("0")),
      Parameter(name = "c", typ = typing.Typed[String]).copy(isLazyParameter = true, defaultValue = Some("'c-field-default'"), editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      Parameter(name = "d", typ = typing.Typed[Long]).copy(isLazyParameter = true, defaultValue = Some("42L")),
      Parameter(name = "e", typ = typing.Typed[Long]).copy(isLazyParameter = true, defaultValue = Some("null"), validators = Nil)
    )
  }

  test("typing result to AvroSinkPrimitiveValueParameter") {
    val longSchema = SchemaBuilder.builder().longType()
    val result = AvroSinkValueParameter(longSchema).valueOr(e => fail(e.toString)).asInstanceOf[SinkSingleValueParameter]
    StandardParameterEnrichment.enrichParameterDefinitions(result.toParameters, SingleComponentConfig.zero) shouldBe List(
      Parameter(name = SinkValueParamName, typ = typing.Typed[Long]).copy(isLazyParameter = true, defaultValue = Some("0"))
    )
  }

  test("typed object with restricted field names") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
        .name(SinkKeyParamName).`type`().stringType().noDefault()
        .name("b").`type`().longType().noDefault()
      .endRecord()
    val result = AvroSinkValueParameter(recordSchema)
    result shouldBe Invalid(NonEmptyList.one(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are Schema version, Key, Value validation mode, Topic""", None)))
  }
}
