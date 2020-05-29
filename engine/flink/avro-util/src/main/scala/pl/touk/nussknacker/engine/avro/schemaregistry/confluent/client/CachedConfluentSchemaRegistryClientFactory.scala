package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.DefaultCache

import scala.concurrent.duration.Duration

class CachedConfluentSchemaRegistryClientFactory(maximumSize: Long, latestSchemaExpirationTime: Option[Duration], schemaExpirationTime: Option[Duration])
  extends ConfluentSchemaRegistryClientFactory with Serializable with LazyLogging {

  //Cache engines are shared by many of CachedConfluentSchemaRegistryClient
  @transient private lazy val schemaCache = new DefaultCache[Schema](maximumSize, schemaExpirationTime, Option.empty)
  @transient private lazy val latestSchemaCache = new DefaultCache[Schema](maximumSize, Option.empty, latestSchemaExpirationTime)

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = confluentClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client, schemaCache, latestSchemaCache)
  }

  protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    CachedSchemaRegistryClient(kafkaConfig)
}

object CachedConfluentSchemaRegistryClientFactory {

  import scala.concurrent.duration._

  private val latestSchemaExpirationTime: Option[FiniteDuration] = Some(5.minutes)
  private val schemaExpirationTime: Option[FiniteDuration] = Some(120.minutes)
  private val defaultMaximumSize: Long = DefaultCache.defaultMaximumSize

  def apply(): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(defaultMaximumSize, latestSchemaExpirationTime, schemaExpirationTime)
}
