package pl.touk.nussknacker.engine.avro

import java.nio.ByteBuffer

import cats.data.Validated
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaRegistryProvider}

class AvroUtils(schemaRegistryProvider: SchemaRegistryProvider[_]) extends Serializable {

  private lazy val schemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  def record(fields: collection.Map[String, _], schema: Schema): GenericData.Record =
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)

  def record(fields: java.util.Map[String, _], schema: Schema): GenericData.Record =
    BestEffortAvroEncoder.encodeRecordOrError(fields, schema)

  def keySchema(topic: String, version: Int): Schema =
    handleClientResponse {
      schemaRegistryClient.getBySubjectAndVersion(AvroUtils.keySubject(topic), version)
    }

  def valueSchema(topic: String, version: Int): Schema =
    handleClientResponse {
      schemaRegistryClient.getBySubjectAndVersion(AvroUtils.valueSubject(topic), version)
    }

  def latestKeySchema(topic: String): Schema =
    handleClientResponse {
      schemaRegistryClient.getLatestSchema(AvroUtils.keySubject(topic))
    }

  def latestValueSchema(topic: String): Schema =
    handleClientResponse {
      schemaRegistryClient.getLatestSchema(AvroUtils.valueSubject(topic))
    }

  private def handleClientResponse(response: => Validated[SchemaRegistryError, Schema]): Schema =
    response.valueOr(ex => throw ex)
}

object AvroUtils {

  private def parser = new Schema.Parser()

  def parsePayloadToByteBuffer(payload: Array[Byte]): ByteBuffer = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.get != 0)
      throw new SerializationException("Unknown magic byte!")
    else
      buffer
  }

  def topicSubject(topic: String, isKey: Boolean): String =
    if (isKey) keySubject(topic) else valueSubject(topic)

  def keySubject(topic: String): String =
    topic + "-key"

  def valueSubject(topic: String): String =
    topic + "-value"

  def parseSchema(avroSchema: String): Schema =
    parser.parse(avroSchema)
}
