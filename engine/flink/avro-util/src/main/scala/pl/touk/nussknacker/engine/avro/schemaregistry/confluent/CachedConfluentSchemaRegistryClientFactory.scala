package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.TypedConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

object CachedConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory {

  private val schemaSubjectVersionCache: mutable.Map[(String, Int), Schema] = TrieMap.empty[(String, Int), Schema]

  import collection.JavaConverters._

  // copied from AbstractKafkaAvroSerDe.configureClientProperties
  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): TypedConfluentSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")

    // It's fat object which can not be serialized
    new CachedSchemaRegistryClient(urls, maxSchemaObject, originals) with ConfluentSchemaRegistryClient with LazyLogging {

      override def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema] = {
        val latestSchemaMetadata = getLatestSchemaMetadata(subject)
        handleClientError {
          schemaSubjectVersionCache.getOrElseUpdate((subject, latestSchemaMetadata.getVersion), {
            logger.debug(s"Cached latest schema for subject: $subject and version: ${latestSchemaMetadata.getVersion}.")
            AvroUtils.parseSchema(latestSchemaMetadata.getSchema)
          })
        }
      }

      override def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema] =
        handleClientError {
          schemaSubjectVersionCache.getOrElseUpdate((subject, version), {
            val schemaMetadata = getSchemaMetadata(subject, version)
            logger.debug(s"Cached schema for subject: $subject and version: ${schemaMetadata.getVersion}.")
            AvroUtils.parseSchema(schemaMetadata.getSchema)
          })
        }
    }
  }
}
