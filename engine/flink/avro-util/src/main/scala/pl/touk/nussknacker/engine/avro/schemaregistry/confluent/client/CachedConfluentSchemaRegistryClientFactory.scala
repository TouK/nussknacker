package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.DefaultCache

import scala.concurrent.duration.Duration

class CachedConfluentSchemaRegistryClientFactory(maximumSize: Long, latestSchemaTtl: Option[Duration], expireAfterAccess: Option[Duration])
  extends ConfluentSchemaRegistryClientFactory with Serializable {

  //Cache engine is shared by many of CachedConfluentSchemaRegistryClient
  lazy private val schemaCache = DefaultCache[Schema](maximumSize, expireAfterAccess)

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = CachedSchemaRegistryClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client, schemaCache, latestSchemaTtl)
  }
}

object CachedConfluentSchemaRegistryClientFactory {

  import scala.concurrent.duration._

  private val defaultExpireAfterAccess: Option[FiniteDuration] = Some(120.minutes)
  private val defaultLatestTtl: Option[FiniteDuration] = Some(5.minutes)
  private val defaultMaximumSize: Long = DefaultCache.defaultMaximumSize

  def apply(): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(defaultMaximumSize, defaultLatestTtl, defaultExpireAfterAccess)
}
