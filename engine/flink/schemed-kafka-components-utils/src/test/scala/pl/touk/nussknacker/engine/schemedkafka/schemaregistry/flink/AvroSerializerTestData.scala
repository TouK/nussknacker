package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.avro.SchemaBuilder
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaId}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client.{
  MockAzureSchemaRegistryClient,
  MockAzureSchemaRegistryClientBuilder
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{
  MockConfluentSchemaRegistryClientBuilder,
  MockSchemaRegistryClient
}

object AvroSerializerTestData {
  val confluentSchemaRegistryId = 6
  val azureSchemaRegistryId     = 9

  val confluentRecord1: GenericRecordWithSchemaId = {
    val schema = SchemaBuilder.record("schema1").fields().requiredString("f1").endRecord()
    val record = new GenericRecordWithSchemaId(schema, confluentSchemaRegistryId, SchemaId.fromInt(11))
    record.put("f1", "str1")
    record
  }

  val confluentRecord2: GenericRecordWithSchemaId = {
    val schema = SchemaBuilder.record("schema2").fields().requiredInt("f2").endRecord()
    val record = new GenericRecordWithSchemaId(schema, confluentSchemaRegistryId, SchemaId.fromInt(22))
    record.put("f2", 5)
    record
  }

  val azureTopic = "azure-topic"

  val azureRecord: GenericRecordWithSchemaId = {
    val fullName = SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(UnspecializedTopicName(azureTopic))
    val schema   = SchemaBuilder.record(fullName).fields().requiredBoolean("f3").endRecord()
    val record = new GenericRecordWithSchemaId(schema, azureSchemaRegistryId, SchemaId.fromString("schema-for-record3"))
    record.put("f3", true)
    record
  }

  def createConfluentClient(): MockSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .registerWithId("test-topic1", confluentRecord1.getSchema, 1, isKey = false, confluentRecord1.getSchemaId.asInt)
    .registerWithId("test-topic2", confluentRecord2.getSchema, 1, isKey = false, confluentRecord2.getSchemaId.asInt)
    .build

  def createAzureClient(): MockAzureSchemaRegistryClient = new MockAzureSchemaRegistryClientBuilder()
    .register(azureTopic, azureRecord.getSchema, 1, isKey = false, azureRecord.getSchemaId.asString)
    .build

}
