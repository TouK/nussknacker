package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaMetadata, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

class CachedConfluentSchemaRegistryClient(val client: CSchemaRegistryClient) extends ConfluentSchemaRegistryClient with LazyLogging {

  private val schemaCache: mutable.Map[String, Schema] = TrieMap.empty[String, Schema]

  //At now we cache only parsing schemaString to Schema
  //@TODO: Use for example caffeine cache
  override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] = {
    val latestSchemaMetadata = client.getLatestSchemaMetadata(subject)
    handleClientError {
      getOrCreate(subject, latestSchemaMetadata.getVersion, {
        logger.debug(s"Cached latest schema for subject: $subject and version: ${latestSchemaMetadata.getVersion}.")
        latestSchemaMetadata
      })
    }
  }

  override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      getOrCreate(subject, version, {
        logger.debug(s"Cached schema for subject: $subject and version: $version.")
        client.getSchemaMetadata(subject, version)
      })
    }

  private def getOrCreate(subject: String, version: Int, opSchemaMetadata: => SchemaMetadata): Schema =
    schemaCache.getOrElseUpdate(s"$subject-$version", {
      AvroUtils.parseSchema(opSchemaMetadata.getSchema)
    })
}


object CachedConfluentSchemaRegistryClient extends ConfluentSchemaRegistryClientFactory {

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
    val client = CachedSchemaRegistryClient(kafkaConfig)
    new CachedConfluentSchemaRegistryClient(client)
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

