package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaWithMetadata
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, SingleValueCache}

import scala.concurrent.duration.FiniteDuration

class CachedConfluentSchemaRegistryClientFactory(maximumSize: Long, schemaExpirationTime: Option[FiniteDuration], versionsCacheExpirationTime: Option[FiniteDuration])
  extends ConfluentSchemaRegistryClientFactory with Serializable with LazyLogging {

  //Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val caches = new SchemaRegistryCaches(maximumSize, schemaExpirationTime, versionsCacheExpirationTime)

  override def create(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = confluentClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client, caches)
  }

  protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    CachedSchemaRegistryClient(kafkaConfig)
}

object CachedConfluentSchemaRegistryClientFactory {

  import scala.concurrent.duration._

  private val versionsCacheExpirationTime: Option[FiniteDuration] = Some(1.minute)
  private val schemaExpirationTime: Option[FiniteDuration] = Some(120.minutes)
  private val defaultMaximumSize: Long = CacheConfig.defaultMaximumSize

  def apply(): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(defaultMaximumSize, schemaExpirationTime, versionsCacheExpirationTime)
}

class SchemaRegistryCaches(maximumSize: Long, schemaExpirationTime: Option[FiniteDuration], versionsCacheExpirationTime: Option[FiniteDuration]) {
   val schemaCache = new DefaultCache[String, SchemaWithMetadata](CacheConfig(maximumSize, schemaExpirationTime, Option.empty))
   val versionsCache = new DefaultCache[String, List[Integer]](CacheConfig(maximumSize, Option.empty, versionsCacheExpirationTime))
   val topicsCache = new SingleValueCache[List[String]](Option.empty, versionsCacheExpirationTime)
}
