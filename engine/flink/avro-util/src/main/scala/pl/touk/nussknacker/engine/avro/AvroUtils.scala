package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.concurrent.TrieMap

class AvroUtils(schemaRegistryClientFactory: SchemaRegistryClientFactory, kafkaConfig: KafkaConfig) extends Serializable {

  private lazy val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

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
    getOrUpdateSchemaBySubjectAndVersion(keySubject(topic), version)
  }

  def valueSchema(topic: String, version: Int): Schema = {
    getOrUpdateSchemaBySubjectAndVersion(valueSubject(topic), version)
  }

  def latestKeySchema(topic: String): Schema = {
    getOrUpdateLatestSchema(keySubject(topic))
  }

  def latestValueSchema(topic: String): Schema = {
    getOrUpdateLatestSchema(valueSubject(topic))
  }

  private def getOrUpdateSchemaBySubjectAndVersion(subject: String, version: Int) = {
    schemaBySubjectAndVersionCache.getOrElseUpdate((subject, version),
      parser.parse(schemaRegistryClient.getSchemaMetadata(subject, version).getSchema))
  }

  private def getOrUpdateLatestSchema(subject: String) = {
    // maybe invalidation after some time?
    lastestSchemaBySubjectCache.getOrElseUpdate(subject,
      parser.parse(schemaRegistryClient.getLatestSchemaMetadata(subject).getSchema))
  }

  private def keySubject(topic: String) =
    topic + "-key"


  private def valueSubject(topic: String) =
    topic + "-value"

}