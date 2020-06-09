package pl.touk.nussknacker.engine.avro.sink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.{GenericContainer, GenericData}
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.avro.KafkaAvroSpec
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, NotValidSchema, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaParseError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory

class FixedKafkaAvroSinkFactorySpec extends KafkaAvroSpec with KafkaAvroSinkSpecMixin  {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val avroSinkFactory = new FixedKafkaAvroSinkFactory[GenericData.Record](processObjectDependencies)

  protected def createSink(topic: String, avroSchemaString: String, output: LazyParameter[GenericContainer]): Sink =
    avroSinkFactory.create(metaData, topic, avroSchemaString, output)(nodeId)

  test("should allow to create sink when #input schema is the same as fxied schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV1.stringSchema, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema -> #sinkSchema)") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV2.stringSchema, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema <- #sinkSchema)") {
    val output = createOutput(FullNameV2.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV1.stringSchema, output)
  }

  test("should throw exception when provided schema is not valid schema avro format") {
    assertThrowsWithParent[SchemaParseError] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, NotValidSchema.stringSchema, output)
    }
  }

  //TODO: add after type validation will be implemented
  ignore("should throw exception when #output schema doesn't match to sink schema") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, FullNameV1.stringSchema, output)
    }
  }
}
