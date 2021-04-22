package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SinkKeyParamName, SinkValueParamName}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

class AvroSinkValueParameterTest extends FunSuite with Matchers {
  private implicit val nodeId: NodeId = NodeId("")

  test("typing result to AvroSinkRecordParameter") {
    val typ = TypingUtils.typeMapDefinition(
      Map(
        "a" -> "String",
        "b" -> Map("c" -> "Long")
      ))

    val result =  AvroSinkValueParameter(typ).valueOr(e => fail(e.toString)).asInstanceOf[AvroSinkRecordParameter]
    result.toParameters.toSet shouldBe Set(
      Parameter(name = "b.c", typ = typing.Typed[java.lang.Long]).copy(
        isLazyParameter = true),
      Parameter(name = "a", typ = typing.Typed[String]).copy(
        isLazyParameter = true,
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)))
    )
  }

  test("typing result to AvroSinkPrimitiveValueParameter") {
    val result = AvroSinkValueParameter(typing.Typed[java.lang.Long]).valueOr(e => fail(e.toString)).asInstanceOf[AvroSinkSingleValueParameter]
    result.toParameters.toSet shouldBe Set(
      Parameter(name = SinkValueParamName, typ = typing.Typed[java.lang.Long]).copy(isLazyParameter = true)
    )
  }

  test("typed object with restricted field names") {
    val typ = TypingUtils.typeMapDefinition(
      Map(
        SinkKeyParamName -> "String",
        "b" -> "Long"
      ))
    val result =  AvroSinkValueParameter(typ)
    result shouldBe Invalid(NonEmptyList.one(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are Schema version, Key, Value validation mode, Topic""", None)))
  }
}
