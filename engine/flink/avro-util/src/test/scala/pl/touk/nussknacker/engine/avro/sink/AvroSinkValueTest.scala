package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.SchemaBuilder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkValueParamName


class AvroSinkValueTest extends FunSuite with Matchers {
  private implicit val nodeId: NodeId = NodeId("")

  test("sink params to AvroSinkRecordValue") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
      .name("a").`type`().longType().noDefault()
      .name("b").`type`()
        .record("B")
        .fields()
          .name("c").`type`().longType().noDefault()
        .endRecord().noDefault()
      .endRecord()

    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
    }

    val parameterValues = Map(
      "a" -> value,
      "b.c" -> value)

    val sinkParam = AvroSinkValueParameter(recordSchema).valueOr(e => fail(e.toString))

    val fields: Map[String, AvroSinkValue] = AvroSinkValue.applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[AvroSinkRecordValue]
      .fields.toMap

    fields("a").asInstanceOf[AvroSinkSingleValue].value shouldBe value

    val b: Map[String, AvroSinkValue] = fields("b").asInstanceOf[AvroSinkRecordValue].fields.toMap
    b("c").asInstanceOf[AvroSinkSingleValue].value shouldBe value
  }

  test("sink params to AvroSinkSingleValue") {
    val longSchema = SchemaBuilder.builder().longType()
    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
    }
    val parameterValues = Map(SinkValueParamName -> value)
    val sinkParam = AvroSinkValueParameter(longSchema).valueOr(e => fail(e.toString))

    AvroSinkValue
      .applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[AvroSinkSingleValue]
      .value shouldBe value
  }
}
