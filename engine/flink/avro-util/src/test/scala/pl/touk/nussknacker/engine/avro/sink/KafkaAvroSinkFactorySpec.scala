package pl.touk.nussknacker.engine.avro.sink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.avro.KafkaAvroSpec
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}

class KafkaAvroSinkFactorySpec extends KafkaAvroSpec with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    val schemaRegistryProvider = createSchemaRegistryProvider(useSpecificAvroReader = false)
    new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
  }

  protected def createSink(topic: String, version: Integer, output: LazyParameter[GenericContainer]): Sink =
    avroSinkFactory.create(metaData, topic, version, output)(nodeId)

  test("should throw exception when schema doesn't exist") {
    assertThrowsWithParent[SchemaSubjectNotFound] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink("not-exists-subject", 1, output)
    }
  }

  test("should throw exception when schema version doesn't exist") {
    assertThrowsWithParent[SchemaVersionNotFound] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, 3, output)
    }
  }

  test("should allow to create sink when #output schema is the same as sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    val sink = createSink(fullnameTopic, 2, output)
  }

  //TODO: add after type validation will be implemented
  ignore("should throw exception when #output schema doesn't match to sink schema") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }
}
