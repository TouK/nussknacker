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
    val exampleAvroEnumTopic: String   = "example-avro-enum"
    val exampleJsonTopic: String       = "example-json"
    val generatedNewSchemaVersion: Int = 3

    val allRegisteredTopics: List[String] =
      List(fullnameTopic, exampleAvroTopic, exampleAvroEnumTopic, exampleJsonTopic).sorted

    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullnameTopic, FullNameV1.schema, 1, isKey = false)
      .register(fullnameTopic, FullNameV2.schema, 2, isKey = false)
      .register(fullnameTopic, PaymentV1.schema, 3, isKey = false)
      .register(fullnameTopic, NestedRecord.schema, 4, isKey = false)
      .register(exampleAvroTopic, AllTypesAvroSchemaWithDefaultValues.schema, 1, isKey = false)
      .register(exampleAvroTopic, AllTypesAvroSchemaWithoutDefaultValues.schema, 2, isKey = false)
      .register(exampleJsonTopic, AllTypesJsonSchemaWithDefaultValues.schema, 1, isKey = false)
      .register(exampleJsonTopic, AllTypesJsonSchemaWithoutDefaultValues.schema, 2, isKey = false)
      .register(exampleAvroEnumTopic, AvroEnum.V1.schema, 1, isKey = false)
      .register(exampleAvroEnumTopic, AvroEnum.V2.schema, 2, isKey = false)
      .register(exampleAvroEnumTopic, AvroEnum.V3.schema, 3, isKey = false)
      .build

    val factory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

}
