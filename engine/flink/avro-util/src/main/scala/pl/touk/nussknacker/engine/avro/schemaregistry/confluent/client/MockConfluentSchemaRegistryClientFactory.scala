package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.CacheConfig

/**
 * MockSchemaRegistryClient must be passed by name, because schemaRegistryMockClient is not serializable.
 */
class MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient: => MockSchemaRegistryClient)
  extends CachedConfluentSchemaRegistryClientFactory(CacheConfig.defaultMaximumSize, None, None, None){

  override protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    schemaRegistryMockClient

}
