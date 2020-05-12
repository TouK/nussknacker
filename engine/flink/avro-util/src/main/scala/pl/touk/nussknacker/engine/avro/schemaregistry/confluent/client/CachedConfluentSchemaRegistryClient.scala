package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.Cache

/**
  * We use there own cache engine because ConfluentCachedClient doesn't cache getLatestSchemaMetadata and getSchemaMetadata
  */
class CachedConfluentSchemaRegistryClient(val client: CSchemaRegistryClient, schemaCache: Cache[Schema], latestSchemaCache: Cache[Schema])
  extends ConfluentSchemaRegistryClient with LazyLogging {

  private val latestCacheNamespace = "latest"

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] = {
    handleClientError {
      latestSchemaCache.getOrCreate(s"$subject-$latestCacheNamespace") {
        logger.debug(s"Cached latest schema for subject: $subject.")
        val schemaMetadata = client.getLatestSchemaMetadata(subject)
        AvroUtils.parseSchema(schemaMetadata.getSchema)
      }
    }
  }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      schemaCache.getOrCreate(s"$subject-$version") {
        logger.debug(s"Cached schema for subject: $subject and version: $version.")
        val schemaMetadata = client.getSchemaMetadata(subject, version)
        AvroUtils.parseSchema(schemaMetadata.getSchema)
      }
    }
}

private[client] object CachedSchemaRegistryClient {

  import collection.JavaConverters._

  def apply(kafkaConfig: KafkaConfig): CCachedSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")
    new CCachedSchemaRegistryClient(urls, maxSchemaObject, originals)
  }
}
