package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.kafka.{
  SchemaRegistryCacheConfig,
  SchemaRegistryClientKafkaConfig,
  UnspecializedTopicName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaId,
  SchemaRegistryClient,
  SchemaRegistryClientFactory,
  SchemaWithMetadata
}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, SingleValueCache}

import scala.collection.mutable

object CachedConfluentSchemaRegistryClientFactory extends CachedConfluentSchemaRegistryClientFactory

class CachedConfluentSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT = ConfluentSchemaRegistryClient

  // Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val caches = mutable.Map[SchemaRegistryClientKafkaConfig, SchemaRegistryCaches]()

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    val client = DefaultConfluentSchemaRegistryClientFactory.createConfluentClient(config)
    val cache = synchronized {
      caches.getOrElseUpdate(
        config, {
          new SchemaRegistryCaches(config.cacheConfig)
        }
      )
    }
    new CachedConfluentSchemaRegistryClient(client, cache)
  }

}

class SchemaRegistryCaches(cacheConfig: SchemaRegistryCacheConfig) extends LazyLogging {

  logger.debug(s"Created ${getClass.getSimpleName} with: $cacheConfig")

  import cacheConfig._

  val latestSchemaIdCache =
    new DefaultCache[String, SchemaId](CacheConfig(maximumSize, parsedSchemaAccessExpirationTime, Option.empty))

  val schemaCache = new DefaultCache[String, SchemaWithMetadata](
    CacheConfig(maximumSize, parsedSchemaAccessExpirationTime, Option.empty)
  )

  val schemaByIdCache = new DefaultCache[SchemaId, SchemaWithMetadata](
    CacheConfig(maximumSize, parsedSchemaAccessExpirationTime, Option.empty)
  )

  val versionsCache =
    new DefaultCache[String, List[Integer]](CacheConfig(maximumSize, Option.empty, availableSchemasExpirationTime))
  val topicsCache = new SingleValueCache[List[UnspecializedTopicName]](Option.empty, availableSchemasExpirationTime)

}
