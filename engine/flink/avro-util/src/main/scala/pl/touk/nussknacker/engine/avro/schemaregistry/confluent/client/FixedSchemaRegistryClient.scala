package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import java.util

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, MockSchemaRegistryClient => CMockSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils

/**
  * This class is kind of wrap on mock. This class always returns only one fixed schema for each request
  */
class FixedSchemaRegistryClient(subject: String, avroSchema: String) extends CMockSchemaRegistryClient with LazyLogging {

  import collection.JavaConverters._

  //It's fake id - not important
  val schemaId = 0

  //Version depends on schema hash code
  lazy val version: Int = avroSchema.hashCode

  lazy val key: String = s"$subject-$version"

  //Some cache for schema
  lazy val schema: Schema = AvroUtils.parseSchema(avroSchema)

  override def getById(id: Int): Schema =
    schema

  override def getBySubjectAndId(subject: String, id: Int): Schema =
    schema

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
