package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import cats.data.Validated
import cats.data.Validated.valid
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.SchemaProvider
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, CachedSchemaRegistryClient => CCachedSchemaRegistryClient, SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaWithMetadata.unknownVersion
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryError, SchemaWithMetadata}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

import scala.jdk.CollectionConverters._

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
        SchemaWithMetadata(schemaMetadata, config)
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
    caches.latestSchemaIdCache.put(subject)(schemaMetadata.getId)
    caches.schemaCache.getOrCreate(s"$subject-${schemaMetadata.getVersion}") {
      logger.debug(s"Cache parsed latest schema for subject: $subject, version: ${schemaMetadata.getVersion}.")
      SchemaWithMetadata(schemaMetadata, config)
    }
  }

  override def getSchemaById(id: Int): SchemaWithMetadata = {
    //Confluent client caches the schema, but in SchemaWithMetadata we do additional processing (e.g. for JSON schema) so we shouldn't do it on each event
    caches.schemaByIdCache.getOrCreate(id) {
      val rawSchema = client.getSchemaById(id)
      SchemaWithMetadata(new SchemaMetadata(id, unknownVersion, rawSchema.schemaType(), rawSchema.references(), rawSchema.canonicalString()), config)
    }
  }

  override def getLatestSchemaId(topic: String, isKey: Boolean): Validated[SchemaRegistryError, Int] = {
    val subject = ConfluentUtils.topicSubject(topic, isKey)
    caches.latestSchemaIdCache.get(subject) match {
      case Some(id) => valid(id)
      case None => getLatestFreshSchema(topic, isKey).map(_.id)
    }
  }
}

private[client] object CachedSchemaRegistryClient {

  import scala.jdk.CollectionConverters._

  def apply(c: SchemaRegistryClientKafkaConfig): CCachedSchemaRegistryClient = {
    val config = new KafkaAvroDeserializerConfig(c.kafkaProperties.asJava)
    val urls = config.getSchemaRegistryUrls
    val maxSchemaObject = config.getMaxSchemasPerSubject
    val originals = config.originalsWithPrefix("")
    val schemaProviders: List[SchemaProvider] = List(new JsonSchemaProvider(), new AvroSchemaProvider())
    new CCachedSchemaRegistryClient(urls, maxSchemaObject, schemaProviders.asJava, originals)
  }
}
