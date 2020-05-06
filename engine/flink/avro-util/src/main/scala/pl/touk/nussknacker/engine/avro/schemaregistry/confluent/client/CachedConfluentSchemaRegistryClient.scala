package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaMetadata}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError

import scala.collection.concurrent.TrieMap

class CachedConfluentSchemaRegistryClient(val config: KafkaAvroDeserializerConfig) extends ConfluentSchemaRegistryClient with LazyLogging {

  lazy val client: CachedSchemaRegistryClient = {
    logger.debug(s"Create MockSchemaRegistryClient with data: $config.")
    new CachedSchemaRegistryClient(
      config.getSchemaRegistryUrls,
      config.getMaxSchemasPerSubject,
      config.originalsWithPrefix("")
    )
  }

  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] = {
    val latestSchemaMetadata = client.getLatestSchemaMetadata(subject)
    handleClientError {
      CachedConfluentSchemaRegistryClient.getOrCreate(subject, latestSchemaMetadata.getVersion, {
        logger.debug(s"Cached latest schema for subject: $subject and version: ${latestSchemaMetadata.getVersion}.")
        latestSchemaMetadata
      })
    }
  }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      CachedConfluentSchemaRegistryClient.getOrCreate(subject, version, {
        logger.debug(s"Cached schema for subject: $subject and version: ${version}.")
        client.getSchemaMetadata(subject, version)
      })
    }
}

object CachedConfluentSchemaRegistryClient extends LazyLogging {

  private val schemaCache = TrieMap.empty[String, Schema]

  def getOrCreate(key: String, opStringSchema: => String): Schema =
    schemaCache.getOrElseUpdate(key, AvroUtils.parseSchema(opStringSchema))

  def getOrCreate(subject: String, version: Int, opSchemaMetadata: => SchemaMetadata): Schema =
    schemaCache.getOrElseUpdate(s"$subject-$version", AvroUtils.extractSchema(opSchemaMetadata))
}
