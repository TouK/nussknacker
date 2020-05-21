package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSpec, TestMockSchemaRegistry}

class KafkaAvroSinkFactorySpec extends KafkaAvroSpec {

  import MockSchemaRegistry._

  override def schemaRegistryClient: ConfluentSchemaRegistryClient = confluentSchemaRegistryMockClient

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

  test("should throw exception when output doesn't match to schema") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }

  test("should not allow to create sink for #input v1 and #output v2") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }

  test("should allow to create sink for #input v1 and #output v1") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should allow to create sink for #input v2 and #output v1") {
    val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  private def createOutput(schema: Schema, data: Map[String, Any]): LazyParameter[GenericContainer] = {
    val record = AvroUtils.createRecord(schema, data)
    new LazyParameter[GenericContainer] {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(record.getSchema)
    }
  }
}

object MockSchemaRegistry extends TestMockSchemaRegistry {

  val fullnameTopic: String = "fullname"

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(fullnameTopic, FullNameV1.schema, 1, isKey = false)
    .register(fullnameTopic, FullNameV1.schema, 2, isKey = false)
    .build

  val mockConfluentSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory =
    createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient)
}
