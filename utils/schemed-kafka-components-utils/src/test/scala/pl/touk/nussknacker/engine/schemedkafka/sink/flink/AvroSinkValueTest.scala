package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import org.apache.avro.SchemaBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.schema.AvroSchemaBasedParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordValue, SinkSingleValue, SinkValue}

class AvroSinkValueTest extends AnyFunSuite with Matchers {
  private implicit val nodeId: NodeId = NodeId("")

  test("sink params to AvroSinkRecordValue") {
    val recordSchema = SchemaBuilder
      .record("A")
      .fields()
      .name("a")
      .`type`()
      .longType()
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
      .endRecord()

    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
      override def evaluate: Context => AnyRef     = ???
    }

    val parameterValues = Map("a" -> value, "b.c" -> value)

    val sinkParam = AvroSchemaBasedParameter(recordSchema, Set.empty).valueOr(e => fail(e.toString))

    val fields: Map[String, SinkValue] = SinkValue
      .applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[SinkRecordValue]
      .fields

    fields("a").asInstanceOf[SinkSingleValue].value shouldBe value

    val b: Map[String, SinkValue] = fields("b").asInstanceOf[SinkRecordValue].fields
    b("c").asInstanceOf[SinkSingleValue].value shouldBe value
  }

  test("sink params to SinkSingleValue") {
    val longSchema = SchemaBuilder.builder().longType()
    val value = new LazyParameter[AnyRef] {
      override def returnType: typing.TypingResult = Typed[java.lang.Long]
      override def evaluate: Context => AnyRef     = ???
    }
    val parameterValues = Map(SinkValueParamName -> value)
    val sinkParam       = AvroSchemaBasedParameter(longSchema, Set.empty).valueOr(e => fail(e.toString))

    SinkValue
      .applyUnsafe(sinkParam, parameterValues)
      .asInstanceOf[SinkSingleValue]
      .value shouldBe value
  }

}
