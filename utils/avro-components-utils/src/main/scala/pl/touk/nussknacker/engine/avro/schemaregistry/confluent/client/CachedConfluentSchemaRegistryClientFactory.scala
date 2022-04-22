package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaWithMetadata
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryCacheConfig}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, SingleValueCache}

class CachedConfluentSchemaRegistryClientFactory(cacheConfig: SchemaRegistryCacheConfig)
  extends ConfluentSchemaRegistryClientFactory with Serializable with LazyLogging {

  logger.debug(s"Created ${getClass.getSimpleName} with: $cacheConfig")

  //Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val caches = new SchemaRegistryCaches(cacheConfig)

  override def create(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = confluentClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client, caches)
  }

  protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    CachedSchemaRegistryClient(kafkaConfig)
}

object CachedConfluentSchemaRegistryClientFactory {

  def apply(cacheConfig: SchemaRegistryCacheConfig): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(cacheConfig)

}

class SchemaRegistryCaches(cacheConfig: SchemaRegistryCacheConfig) {
  import cacheConfig._
  val schemaCache = new DefaultCache[String, SchemaWithMetadata](CacheConfig(maximumSize, parsedSchemaAccessExpirationTime, Option.empty))
  val versionsCache = new DefaultCache[String, List[Integer]](CacheConfig(maximumSize, Option.empty, availableSchemasExpirationTime))
  val topicsCache = new SingleValueCache[List[String]](Option.empty, availableSchemasExpirationTime)
}
