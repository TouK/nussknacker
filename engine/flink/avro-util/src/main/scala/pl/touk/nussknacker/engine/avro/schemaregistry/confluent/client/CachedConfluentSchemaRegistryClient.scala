package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import scala.collection.JavaConverters._

/**
  * We use there own cache engine because ConfluentCachedClient doesn't cache getLatestSchemaMetadata and getSchemaMetadata
  */
class CachedConfluentSchemaRegistryClient(val client: CSchemaRegistryClient, caches: SchemaRegistryCaches)
  extends ConfluentSchemaRegistryClient with LazyLogging {

  override def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      latestSchemaRequest(subject)
    }

  override def getLatestSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.latestSchemaCache.getOrCreate(subject) {
        logger.debug(s"Cache latest schema for subject: $subject.")
        latestSchemaRequest(subject)
      }
    }

  override def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, Schema] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.schemaCache.getOrCreate(s"$subject-$version") {
        logger.debug(s"Cache schema for subject: $subject and version: $version.")
        val schemaMetadata = client.getSchemaMetadata(subject, version)
        AvroUtils.parseSchema(schemaMetadata.getSchema)
      }
    }


  override def getAllTopics: Validated[SchemaRegistryError, List[String]] = handleClientError {
    caches.topicsCache.getOrCreate {
      client.getAllSubjects.asScala.toList.collect(ConfluentUtils.topicFromSubject)
    }
  }

  override def getAllVersions(topic: String, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]] = handleClientError {
    val subject = ConfluentUtils.topicSubject(topic, isKey)
    caches.versionsCache.getOrCreate(subject) {
      client.getAllVersions(subject).asScala.toList
    }
  }

  private def latestSchemaRequest(subject: String): Schema = {
    val schemaMetadata = client.getLatestSchemaMetadata(subject)

    caches.schemaCache.getOrCreate(s"$subject-${schemaMetadata.getVersion}") {
      logger.debug(s"Cache parsed latest schema for subject: $subject, version: ${schemaMetadata.getVersion}.")
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
