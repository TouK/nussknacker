package pl.touk.nussknacker.engine.avro.sink.flink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.apache.avro.SchemaBuilder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SinkKeyParamName, SinkValueParamName}

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
      .endRecord()

    val result =  AvroSinkValueParameter(recordSchema).valueOr(e => fail(e.toString)).asInstanceOf[AvroSinkRecordParameter]
    result.toParameters.toSet shouldBe Set(
      Parameter(name = "a", typ = typing.Typed[String]).copy(isLazyParameter = true, editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      Parameter(name = "b.c", typ = typing.Typed[Long]).copy(isLazyParameter = true)
    )
  }

  test("typing result to AvroSinkPrimitiveValueParameter") {
    val longSchema = SchemaBuilder.builder().longType()
    val result = AvroSinkValueParameter(longSchema).valueOr(e => fail(e.toString)).asInstanceOf[AvroSinkSingleValueParameter]
    result.toParameters.toSet shouldBe Set(
      Parameter(name = SinkValueParamName, typ = typing.Typed[Long]).copy(isLazyParameter = true)
    )
  }

  test("typed object with restricted field names") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
        .name(SinkKeyParamName).`type`().stringType().noDefault()
        .name("b").`type`().longType().noDefault()
      .endRecord()
    val result =  AvroSinkValueParameter(recordSchema)
    result shouldBe Invalid(NonEmptyList.one(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are Schema version, Key, Value validation mode, Topic""", None)))
  }
}
