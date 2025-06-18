package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientBuilder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

trait KafkaAvroSinkSpecMixin {

  object KafkaAvroSinkMockSchemaRegistry {

    val fullnameTopic: String          = "fullname"
    val exampleAvroTopic: String       = "example-avro"
    val exampleJsonTopic: String       = "example-json"
    val generatedNewSchemaVersion: Int = 3

    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullnameTopic, FullNameV1.confluentSchema, 1, isKey = false)
      .register(fullnameTopic, FullNameV2.confluentSchema, 2, isKey = false)
      .register(fullnameTopic, PaymentV1.confluentSchema, 3, isKey = false)
      .register(fullnameTopic, NestedRecord.confluentSchema, 4, isKey = false)
      .register(exampleAvroTopic, AllTypesAvroSchemaWithDefaultValues.confluentSchema, 1, isKey = false)
      .register(exampleAvroTopic, AllTypesAvroSchemaWithoutDefaultValues.confluentSchema, 2, isKey = false)
      .register(exampleJsonTopic, AllTypesJsonSchemaWithDefaultValues.schema, 1, isKey = false)
      .register(exampleJsonTopic, AllTypesJsonSchemaWithoutDefaultValues.schema, 2, isKey = false)
      .build

    val factory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

}
