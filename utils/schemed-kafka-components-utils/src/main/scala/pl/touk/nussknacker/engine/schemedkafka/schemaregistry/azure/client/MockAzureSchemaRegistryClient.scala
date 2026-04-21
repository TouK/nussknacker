package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.core.exception.ResourceNotFoundException
import com.azure.data.schemaregistry.models.{SchemaFormat, SchemaProperties}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaId,
  SchemaRegistryError,
  SchemaTopicError,
  SchemaVersionError,
  SchemaWithMetadata
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy

import java.lang.reflect.Constructor
import java.util.UUID
import scala.collection.mutable

/**
 * Mock implementation of Schema Registry client for Azure that can be used for tests. This version is NOT thread safe.
 * Schema data is stored in memory and is not persistent or shared across instances.
 */
class MockAzureSchemaRegistryClient extends AzureSchemaRegistryClient {

  private val schemasById          = mutable.Map.empty[String, ParsedSchema]
  private val versionsBySchemaName = mutable.Map.empty[String, mutable.ArrayBuffer[StoredSchema]]
  private val registeredTopics     = mutable.Set.empty[UnspecializedTopicName]

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = {
    val schema = schemasById.getOrElse(id.asString, throw new IllegalArgumentException(s"Schema with id $id not found"))
    SchemaWithMetadata(schema, id)
  }

  override protected def getByTopicAndVersion(
      topic: UnspecializedTopicName,
      version: Int,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] =
    getOneMatchingSchemaName(topic, isKey).andThen { name =>
      versionsBySchemaName.getOrElse(name, mutable.ArrayBuffer.empty).find(_.version == version) match {
        case Some(stored) => Valid(SchemaWithMetadata(stored.schema, SchemaId.fromString(stored.id)))
        case None         => Invalid(SchemaVersionError(s"Version $version of schema $name not found"))
      }
    }

  override protected def getLatestFreshSchema(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] =
    getOneMatchingSchemaName(topic, isKey).andThen { name =>
      versionsBySchemaName.getOrElse(name, mutable.ArrayBuffer.empty).maxByOption(_.version) match {
        case Some(stored) => Valid(SchemaWithMetadata(stored.schema, SchemaId.fromString(stored.id)))
        case None         => Invalid(SchemaTopicError(s"Schema for topic: $topic not found"))
      }
    }

  override def getAllTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val matchStrategy = SchemaNameTopicMatchStrategy(registeredTopics.toList)
    getAllFullSchemaNames.map(matchStrategy.getAllMatchingTopics(_, isKey = false))
  }

  override def getAllVersions(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, List[Integer]] =
    getOneMatchingSchemaName(topic, isKey).map { name =>
      versionsBySchemaName
        .getOrElse(name, mutable.ArrayBuffer.empty)
        .map(_.version)
        .map(Integer.valueOf)
        .toList
    }

  def register(
      topic: UnspecializedTopicName,
      isKey: Boolean,
      schema: ParsedSchema,
      version: Integer = null,
      id: String = null
  ): SchemaId = {
    val schemaNameBasedOnTopic = SchemaNameTopicMatchStrategy.schemaNameFromTopicName(topic, isKey)
    val avroSchema             = checkAvroSchema(schema).rawSchema()
    if (avroSchema.getType != Schema.Type.RECORD) {
      storeIfAbsent(schema, Some(schemaNameBasedOnTopic), Option(version), Option(id))
    }
    registeredTopics.add(topic)
    SchemaId.fromString(storeIfAbsent(schema, Some(avroSchema.getFullName), Option(version), Option(id)).getId)
  }

  override protected def getAllFullSchemaNames: Validated[SchemaRegistryError, List[String]] =
    Valid(versionsBySchemaName.keys.toList)

  override def registerSchema(topicName: UnspecializedTopicName, isKey: Boolean, schema: ParsedSchema): SchemaId = {
    val schemaId = super.registerSchema(topicName, isKey, schema)
    registeredTopics.add(topicName)
    schemaId
  }

  override def registerSchemaVersionIfNotExists(
      schema: ParsedSchema,
      forceSchemaNameOpt: Option[String] = None
  ): SchemaProperties = {
    storeIfAbsent(schema, forceSchemaNameOpt)
  }

  private def storeIfAbsent(
      schema: ParsedSchema,
      fullName: Option[String] = None,
      version: Option[Int] = None,
      id: Option[String] = None
  ): SchemaProperties = synchronized {
    val normalizedSchema = schema.normalize()

    val storedId = id
      .orElse(schemasById.find({ case (_, schema) => schema == normalizedSchema }).map(_._1))
      .getOrElse(UUID.randomUUID().toString)
    val storedName = fullName.getOrElse(checkAvroSchema(schema).rawSchema().getFullName)

    val buf = versionsBySchemaName.getOrElseUpdate(storedName, mutable.ArrayBuffer.empty)
    val storedSchema = buf.find(_.schema == normalizedSchema) match {
      case Some(existing) =>
        existing
      case None =>
        val newVersion = version.orElse(buf.maxByOption(_.version).map(_.version + 1)).getOrElse(1)
        val stored     = StoredSchema(storedId, newVersion, storedName, normalizedSchema)
        buf.append(stored)
        if (!schemasById.contains(storedId)) {
          schemasById.put(storedId, normalizedSchema)
        }
        stored
    }

    createSchemaProperties(storedSchema, storedName)
  }

  override def getSchemaIdByContent(schema: AvroSchema): SchemaId = {
    val fullName         = schema.rawSchema().getFullName
    val normalizedSchema = schema.normalize()
    versionsBySchemaName
      .get(fullName)
      .orElse(throw new ResourceNotFoundException(s"Cannot find a matching schema for $fullName", null))
      .flatMap(_.find(_.schema == normalizedSchema))
      .map(_.id)
      .map(SchemaId.fromString)
      .getOrElse(throw new ResourceNotFoundException(s"Cannot find a matching schema version for $fullName", null))
  }

  private def createSchemaProperties(storedSchema: StoredSchema, fullName: String): SchemaProperties = {
    val constructor = classOf[SchemaProperties].getDeclaredConstructors
      .find(_.getParameterCount == 5)
      .get
      .asInstanceOf[Constructor[SchemaProperties]]
    constructor.setAccessible(true)
    // call private constructor - doing it the normal way would require manual construction of HTTP response objects
    // SchemaProperties(String id, SchemaFormat format, String groupName, String name, int version) constructor
    constructor.newInstance(storedSchema.id, SchemaFormat.AVRO, "mockGroup", fullName, storedSchema.version)
  }

}

private[client] case class StoredSchema(id: String, version: Int, name: String, schema: ParsedSchema)
