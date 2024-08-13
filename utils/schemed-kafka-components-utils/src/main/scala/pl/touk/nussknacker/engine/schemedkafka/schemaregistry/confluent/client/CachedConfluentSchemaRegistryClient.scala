package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaRegistryError, SchemaWithMetadata}

import scala.jdk.CollectionConverters._

/**
 * We use there own cache engine because ConfluentCachedClient doesn't cache getLatestSchemaMetadata and getSchemaMetadata
 */
class CachedConfluentSchemaRegistryClient(val client: CSchemaRegistryClient, caches: SchemaRegistryCaches)
    extends ConfluentSchemaRegistryClient
    with LazyLogging {

  override def getLatestFreshSchema(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      latestSchemaRequest(subject)
    }

  override def getByTopicAndVersion(
      topic: UnspecializedTopicName,
      version: Int,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.schemaCache.getOrCreate(s"$subject-$version") {
        logger.debug(s"Cache schema for subject: $subject and version: $version.")
        val schemaMetadata = client.getSchemaMetadata(subject, version)
        ConfluentUtils.toSchemaWithMetadata(schemaMetadata)
      }
    }

  override def getAllTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    handleClientError {
      caches.topicsCache.getOrCreate {
        client.getAllSubjects.asScala.toList.collect(ConfluentUtils.topicFromSubject).map(UnspecializedTopicName.apply)
      }
    }

  override def getAllVersions(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, List[Integer]] =
    handleClientError {
      val subject = ConfluentUtils.topicSubject(topic, isKey)
      caches.versionsCache.getOrCreate(subject) {
        client.getAllVersions(subject).asScala.toList
      }
    }

  private def latestSchemaRequest(subject: String): SchemaWithMetadata = {
    val schemaMetadata = client.getLatestSchemaMetadata(subject)
    caches.latestSchemaIdCache.put(subject)(SchemaId.fromInt(schemaMetadata.getId))
    caches.schemaCache.getOrCreate(s"$subject-${schemaMetadata.getVersion}") {
      logger.debug(s"Cache parsed latest schema for subject: $subject, version: ${schemaMetadata.getVersion}.")
      ConfluentUtils.toSchemaWithMetadata(schemaMetadata)
    }
  }

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = {
    // Confluent client caches the schema, but in SchemaWithMetadata we do additional processing (e.g. for JSON schema) so we shouldn't do it on each event
    caches.schemaByIdCache.getOrCreate(id) {
      val schema = client.getSchemaById(id.asInt)
      SchemaWithMetadata(schema, id)
    }
  }

}
