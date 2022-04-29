package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaWithMetadata
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryCacheConfig}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, SingleValueCache}

import scala.collection.mutable

object CachedConfluentSchemaRegistryClientFactory extends CachedConfluentSchemaRegistryClientFactory

class CachedConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory {

  //Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val caches = mutable.Map[KafkaConfig, SchemaRegistryCaches]()

  override def create(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = confluentClient(kafkaConfig)
    val c = synchronized {
      caches.getOrElseUpdate(kafkaConfig, {
        new SchemaRegistryCaches(kafkaConfig.schemaRegistryCacheConfig)
      })
    }
    new CachedConfluentSchemaRegistryClient(client, c)
  }

  protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    CachedSchemaRegistryClient(kafkaConfig)
}

class SchemaRegistryCaches(cacheConfig: SchemaRegistryCacheConfig) extends LazyLogging {

  logger.debug(s"Created ${getClass.getSimpleName} with: $cacheConfig")

  import cacheConfig._

  val schemaCache = new DefaultCache[String, SchemaWithMetadata](CacheConfig(maximumSize, parsedSchemaAccessExpirationTime, Option.empty))
  val versionsCache = new DefaultCache[String, List[Integer]](CacheConfig(maximumSize, Option.empty, availableSchemasExpirationTime))
  val topicsCache = new SingleValueCache[List[String]](Option.empty, availableSchemasExpirationTime)

}
