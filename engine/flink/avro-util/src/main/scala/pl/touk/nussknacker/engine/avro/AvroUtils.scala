package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

import scala.collection.concurrent.TrieMap

class AvroUtils(schemaRegistryClient: SchemaRegistryClient) extends Serializable {

  private lazy val parsedSchemaCache = TrieMap.empty[String, Schema]

  private lazy val schemaBySubjectAndVersionCache = TrieMap.empty[(String, Int), Schema]

  private lazy val lastestSchemaBySubjectCache = TrieMap.empty[String, Schema]

  private def parser = new Schema.Parser()

  def record(fields: collection.Map[String, _], schemaString: String): GenericData.Record = {
    val schema = parsedSchemaCache.getOrElseUpdate(schemaString, parser.parse(schemaString))
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def record(fields: collection.Map[String, _], schema: Schema): GenericData.Record = {
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)
  }

  def record(fields: java.util.Map[String, _], schemaString: String): GenericData.Record = {
    val schema = parsedSchemaCache.getOrElseUpdate(schemaString, parser.parse(schemaString))
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

  private def getOrUpdateSchemaBySubjectAndVersion(subject: String, version: Int) = {
    schemaBySubjectAndVersionCache.getOrElseUpdate((subject, version),
      parser.parse(schemaRegistryClient.schemaBySubjectAndVersion(subject, version)))
  }

  private def getOrUpdateLatestSchema(subject: String) = {
    // maybe invalidation after some time?
    lastestSchemaBySubjectCache.getOrElseUpdate(subject,
      parser.parse(schemaRegistryClient.latestSchema(subject)))
  }

}

object AvroUtils {

  def keySubject(topic: String) =
    topic + "-key"

  def valueSubject(topic: String) =
    topic + "-value"
}
