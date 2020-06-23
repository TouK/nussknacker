package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.DefaultCache

import scala.concurrent.duration.Duration

class CachedConfluentSchemaRegistryClientFactory(maximumSize: Long, latestSchemaExpirationTime: Option[Duration],
                                                 schemaExpirationTime: Option[Duration], versionsCacheExpirationTime: Option[Duration])
  extends ConfluentSchemaRegistryClientFactory with Serializable with LazyLogging {

  //Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val caches = new SchemaRegistryCaches(maximumSize, latestSchemaExpirationTime, schemaExpirationTime, versionsCacheExpirationTime)

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = confluentClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client, caches)
  }

  protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    CachedSchemaRegistryClient(kafkaConfig)
}

object CachedConfluentSchemaRegistryClientFactory {

  import scala.concurrent.duration._

  private val latestSchemaExpirationTime: Option[FiniteDuration] = Some(5.minute)
  private val versionsCacheExpirationTime: Option[FiniteDuration] = Some(1.minute)
  private val schemaExpirationTime: Option[FiniteDuration] = Some(120.minutes)
  private val defaultMaximumSize: Long = DefaultCache.defaultMaximumSize

  def apply(): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(defaultMaximumSize, latestSchemaExpirationTime,
      schemaExpirationTime, versionsCacheExpirationTime)
}

class SchemaRegistryCaches(maximumSize: Long, latestSchemaExpirationTime: Option[Duration],
                           schemaExpirationTime: Option[Duration], versionsCacheExpirationTime: Option[Duration]) {
   val schemaCache = new DefaultCache[Schema](maximumSize, schemaExpirationTime, Option.empty)
   val latestSchemaCache = new DefaultCache[Schema](maximumSize, Option.empty, latestSchemaExpirationTime)

   val topicsCache = new DefaultCache[List[String]](1, Option.empty, versionsCacheExpirationTime)
   val versionsCache = new DefaultCache[List[Integer]](maximumSize, Option.empty, versionsCacheExpirationTime)
}