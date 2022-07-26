package pl.touk.nussknacker.engine.avro.sink.flink

import org.apache.avro.SchemaBuilder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameterExtractor
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordValue, SinkSingleValue, SinkValue}

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

    val sinkParam = new AvroSinkValueParameterExtractor().extract(recordSchema).valueOr(e => fail(e.toString))

    val fields: Map[String, SinkValue] = SinkValue.applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[SinkRecordValue]
      .fields.toMap

    fields("a").asInstanceOf[SinkSingleValue].value shouldBe value

    val b: Map[String, SinkValue] = fields("b").asInstanceOf[SinkRecordValue].fields.toMap
    b("c").asInstanceOf[SinkSingleValue].value shouldBe value
  }

  test("sink params to SinkSingleValue") {
    val longSchema = SchemaBuilder.builder().longType()
    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
    }
    val parameterValues = Map(SinkValueParamName -> value)
    val sinkParam = new AvroSinkValueParameterExtractor().extract(longSchema).valueOr(e => fail(e.toString))

    SinkValue
      .applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[SinkSingleValue]
      .value shouldBe value
  }
}
