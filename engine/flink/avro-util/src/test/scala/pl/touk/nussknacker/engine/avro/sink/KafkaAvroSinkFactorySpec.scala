package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.avro.{KafkaAvroSpec, TestMockSchemaRegistry}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}

class KafkaAvroSinkFactorySpec extends KafkaAvroSpec with KafkaAvroSinkSpec {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: ConfluentSchemaRegistryClient =
    confluentSchemaRegistryMockClient

  override protected def topics: List[(String, Int)] = List(
    (fullnameTopic, 2)
  )

  private def createAvroSinkFactory: KafkaAvroSinkFactory = {
    val schemaRegistryProvider = ConfluentSchemaRegistryProvider[AnyRef](
      mockConfluentSchemaRegistryClientFactory,
      processObjectDependencies,
      useSpecificAvroReader = false,
      formatKey = false
    )

    new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
  }

  private def createSink(topic: String, version: Integer, output: LazyParameter[GenericContainer]) =
    createAvroSinkFactory.create(metaData, topic, version, output)(nodeId)

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

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema -> #sinkSchema)") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 2, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema <- #sinkSchema)") {
    val output = createOutput(FullNameV2.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  //TODO: Implement this
  ignore("should throw exception when #output schema doesn't match to sink schema") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }
}

object KafkaAvroSinkMockSchemaRegistry extends TestMockSchemaRegistry {

  val fullnameTopic: String = "fullname"

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(fullnameTopic, FullNameV1.schema, 1, isKey = false)
    .register(fullnameTopic, FullNameV2.schema, 2, isKey = false)
    .build

  val mockConfluentSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory =
    createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient)
}
