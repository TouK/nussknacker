package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.SchemaProvider
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaWithMetadata}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

import scala.collection.JavaConverters._

/**
 * We use there own cache engine because ConfluentCachedClient doesn't cache getLatestSchemaMetadata and getSchemaMetadata
 */
class CachedConfluentSchemaRegistryClient(val client: CSchemaRegistryClient, caches: SchemaRegistryCaches, val config: SchemaRegistryClientKafkaConfig)
  extends ConfluentSchemaRegistryClient with LazyLogging {

  override def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      latestSchemaRequest(subject)
    }

  override def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.schemaCache.getOrCreate(s"$subject-$version") {
        logger.debug(s"Cache schema for subject: $subject and version: $version.")
        val schemaMetadata = client.getSchemaMetadata(subject, version)
        withExtraSchemaTypes(SchemaWithMetadata(schemaMetadata))
      }
    }


  override def getAllTopics: Validated[SchemaRegistryError, List[String]] =
    handleClientError {
      caches.topicsCache.getOrCreate {
        client.getAllSubjects.asScala.toList.collect(ConfluentUtils.topicFromSubject)
      }
    }

  override def getAllVersions(topic: String, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.versionsCache.getOrCreate(subject) {
        client.getAllVersions(subject).asScala.toList
      }
    }

  private def latestSchemaRequest(subject: String): SchemaWithMetadata = {
    val schemaMetadata = client.getLatestSchemaMetadata(subject)

    caches.schemaCache.getOrCreate(s"$subject-${schemaMetadata.getVersion}") {
      logger.debug(s"Cache parsed latest schema for subject: $subject, version: ${schemaMetadata.getVersion}.")
      withExtraSchemaTypes(SchemaWithMetadata(schemaMetadata))
    }
  }

  override def getSchemaById(id: Int): SchemaWithMetadata = {
    //todo schemaCache is subject-version keyed, how to get it by id
    val rawSchema = client.getSchemaById(id)
    withExtraSchemaTypes(id, rawSchema)
  }

  private def withExtraSchemaTypes(id: Int, rawSchema: ParsedSchema) = {
    (rawSchema, config.avroPlainTextSerialization) match {
      case (schema: AvroSchema, Some(true)) => SchemaWithMetadata(AvroSchemaWithJsonPayload(schema), id)
      case _ => SchemaWithMetadata(rawSchema, id)

    }
  }

  private def withExtraSchemaTypes(schemaMetadata: SchemaWithMetadata): SchemaWithMetadata = {
    withExtraSchemaTypes(schemaMetadata.id, schemaMetadata.schema)
  }
}

private[client] object CachedSchemaRegistryClient {

  import collection.JavaConverters._

  def apply(c: SchemaRegistryClientKafkaConfig): CCachedSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(c.kafkaProperties.asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")
    val schemaProviders: List[SchemaProvider] = List(new JsonSchemaProvider(), new AvroSchemaProvider())
    new CCachedSchemaRegistryClient(urls, maxSchemaObject, schemaProviders.asJava, originals)
  }
}
