package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.CacheConfig

object TestSchemaRegistryClientFactory {
  def apply(schemaRegistryMockClient: CSchemaRegistryClient): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(CacheConfig.defaultMaximumSize, None, None, None) {
      override protected def confluentClient(kafkaConfig: KafkaConfig): CSchemaRegistryClient =
        schemaRegistryMockClient
    }
}
