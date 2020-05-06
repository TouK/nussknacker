package pl.touk.nussknacker.engine.avro.fixed

import java.util

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaMetadata}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClient

/**
  * This class is kind of wrap on mock. This class always returns only one fixed schema for each request
  */
class FixedSchemaRegistryClient(subject: String, avroSchema: String) extends MockSchemaRegistryClient with LazyLogging {

  import collection.JavaConverters._

  //Version depends on schema hash code
  lazy val version: Int = avroSchema.hashCode

  lazy val key: String = s"$subject-$version"

  //It's fake id - not important
  val schemaId = 0

  override def getById(id: Int): Schema =
    CachedConfluentSchemaRegistryClient.getOrCreate(key, avroSchema)

  override def getBySubjectAndId(subject: String, id: Int): Schema =
    CachedConfluentSchemaRegistryClient.getOrCreate(key, avroSchema)

  override def getSchemaMetadata(subject: String, version: Int): SchemaMetadata =
    new SchemaMetadata(schemaId, avroSchema.hashCode, avroSchema)

  override def getLatestSchemaMetadata(subject: String): SchemaMetadata =
    new SchemaMetadata(schemaId, avroSchema.hashCode, avroSchema)

  override def getAllSubjects: util.Collection[String] =
    List(subject).asJavaCollection

  override def getAllSubjectsById(id: Int): util.Collection[String] =
    List(subject).asJava

  override def getVersion(subject: String, schema: Schema): Int =
    version

  override def getAllVersions(subject: String): util.List[Integer] =
    List(Integer.valueOf(version)).asJava

  override def getId(subject: String, schema: Schema): Int =
    schemaId
}
