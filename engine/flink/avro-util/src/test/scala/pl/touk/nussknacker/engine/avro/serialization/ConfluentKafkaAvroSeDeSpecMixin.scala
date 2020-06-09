package pl.touk.nussknacker.engine.avro.serialization

import pl.touk.nussknacker.engine.avro.TestSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schema.FullNameV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}

trait ConfluentKafkaAvroSeDeSpecMixin {
  object MockSchemaRegistry {
    final val fullNameTopic = "full-name"

    val schemaRegistryMockClient: CSchemaRegistryClient =  new MockConfluentSchemaRegistryClientBuilder()
      .register(fullNameTopic, FullNameV1.schema, 1, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }
}
