package pl.touk.nussknacker.engine.avro.sink

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.util.typing.TypingUtils


class AvroSinkValueTest extends FunSuite with Matchers {
  private implicit val nodeId = NodeId("")

  test("sink params to AvroSinkRecordValue") {
    val typ = TypingUtils.typeMapDefinition(
      Map(
        "a" -> "Long",
        "b" -> Map("c" -> "Long")
      ))

    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
    }

    val parameterValues = Map(
      "a" -> value,
      "b.c" -> value)

    val sinkParam = AvroSinkValueParameter(typ).valueOr(e => fail(e.toString))

    val fields = AvroSinkValue.applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[AvroSinkRecordValue]
      .fields

    fields("a").asInstanceOf[AvroSinkSingleValue].value shouldBe value
    fields("b").asInstanceOf[AvroSinkRecordValue].fields("c").asInstanceOf[AvroSinkSingleValue].value shouldBe value
  }

  test("sink params to AvroSinkSingleValue") {
    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
    }
    val parameterValues = Map(SinkValueParamName -> value)
    val sinkParam = AvroSinkValueParameter(typing.Typed[java.lang.Long]).valueOr(e => fail(e.toString))

    AvroSinkValue
      .applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[AvroSinkSingleValue]
      .value shouldBe value
  }
}
