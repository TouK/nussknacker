package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider

import scala.collection.concurrent.TrieMap

class AvroUtils(schemaRegistryProvider: SchemaRegistryProvider[_]) extends Serializable {

  private lazy val schemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  private lazy val parsedSchemaCache = TrieMap.empty[String, Schema]

  private lazy val schemaBySubjectAndVersionCache = TrieMap.empty[(String, Int), Schema]

  private lazy val lastestSchemaBySubjectCache = TrieMap.empty[String, Schema]

  def record(fields: collection.Map[String, _], schemaString: String): GenericData.Record = {
    val schema = parsedSchemaCache.getOrElseUpdate(schemaString, AvroUtils.createSchema(schemaString))
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def record(fields: collection.Map[String, _], schema: Schema): GenericData.Record = {
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def record(fields: java.util.Map[String, _], schemaString: String): GenericData.Record = {
    val schema = parsedSchemaCache.getOrElseUpdate(schemaString, AvroUtils.createSchema(schemaString))
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def record(fields: java.util.Map[String, _], schema: Schema): GenericData.Record = {
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def keySchema(topic: String, version: Int): Schema = {
    getOrUpdateSchemaBySubjectAndVersion(AvroUtils.keySubject(topic), version)
  }

  def valueSchema(topic: String, version: Int): Schema = {
    getOrUpdateSchemaBySubjectAndVersion(AvroUtils.valueSubject(topic), version)
  }

  def latestKeySchema(topic: String): Schema = {
    getOrUpdateLatestSchema(AvroUtils.keySubject(topic))
  }

  def latestValueSchema(topic: String): Schema = {
    getOrUpdateLatestSchema(AvroUtils.valueSubject(topic))
  }

  private def getOrUpdateSchemaBySubjectAndVersion(subject: String, version: Int): Schema = {
    schemaBySubjectAndVersionCache.getOrElseUpdate((subject, version),
      schemaRegistryClient.getBySubjectAndVersion(subject, version))
  }

  private def getOrUpdateLatestSchema(subject: String) = {
    // maybe invalidation after some time?
    lastestSchemaBySubjectCache.getOrElseUpdate(subject,
      schemaRegistryClient.getLatestSchema(subject))
  }

}

object AvroUtils extends Serializable {

  private def parser = new Schema.Parser()

  def keySubject(topic: String): String =
    topic + "-key"

  def valueSubject(topic: String): String =
    topic + "-value"

  def createSchema(avroSchema: String): Schema =
    parser.parse(avroSchema)

}
