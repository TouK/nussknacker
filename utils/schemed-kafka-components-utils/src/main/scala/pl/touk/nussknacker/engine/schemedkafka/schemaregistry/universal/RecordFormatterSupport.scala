package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.{AvroMessageFormatter, AvroMessageReader}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.nio.charset.StandardCharsets

class RecordFormatterSupportDispatcher(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient) {

  private val supportBySchemaType =
    UniversalSchemaSupportDispatcher(kafkaConfig).supportBySchemaType
      // TODO_PAWEL jakos inaczej moze niz takim filtrowaniem
      .filterKeysNow(e => e == JsonSchema.TYPE)

      .mapValuesNow(_.recordFormatterSupport(schemaRegistryClient))

  def forSchemaType(schemaType: String): RecordFormatterSupport =
    supportBySchemaType.getOrElse(schemaType, throw new UnsupportedSchemaType(schemaType))

}

trait RecordFormatterSupport {
  def formatMessage(data: Any): Json
  def readKeyMessage(topic: TopicName.ForSource, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte]
  def readValueMessage(topic: TopicName.ForSource, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte]
}

object JsonPayloadRecordFormatterSupport extends RecordFormatterSupport {
  override def formatMessage(data: Any): Json =
    ToJsonEncoder(failOnUnknown = false, classLoader = getClass.getClassLoader).encode(data)

  override def readKeyMessage(
      topic: TopicName.ForSource,
      schemaOpt: Option[ParsedSchema],
      jsonObj: Json
  ): Array[Byte] =
    readMessage(topic, schemaOpt, jsonObj)

  override def readValueMessage(
      topic: TopicName.ForSource,
      schemaOpt: Option[ParsedSchema],
      jsonObj: Json
  ): Array[Byte] =
    readMessage(topic, schemaOpt, jsonObj)

  private def readMessage(topic: TopicName.ForSource, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] =
    jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other           => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }

}

class AvroPayloadRecordFormatterSupport(keyMessageReader: AvroMessageReader, valueMessageReader: AvroMessageReader)
    extends RecordFormatterSupport {

  override def formatMessage(data: Any): Json = AvroMessageFormatter.asJson(data)

  override def readKeyMessage(
      topic: TopicName.ForSource,
      schemaOpt: Option[ParsedSchema],
      jsonObj: Json
  ): Array[Byte] =
    keyMessageReader.readJson(
      jsonObj,
      schemaOpt
        .getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None."))
        .asInstanceOf[AvroSchema]
        .rawSchema(),
      topic
    )

  override def readValueMessage(
      topic: TopicName.ForSource,
      schemaOpt: Option[ParsedSchema],
      jsonObj: Json
  ): Array[Byte] =
    valueMessageReader.readJson(
      jsonObj,
      schemaOpt
        .getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None."))
        .asInstanceOf[AvroSchema]
        .rawSchema(),
      topic
    )

}
