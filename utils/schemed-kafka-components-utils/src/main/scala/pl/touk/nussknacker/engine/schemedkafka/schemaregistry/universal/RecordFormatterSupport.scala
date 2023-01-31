package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.{AvroMessageFormatter, AvroMessageReader}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.nio.charset.StandardCharsets

class RecordFormatterSupportDispatcher(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient) {

  private val supportBySchemaType = UniversalSchemaSupport.supportedSchemaTypes.toList.map { schemaType =>
    schemaType -> UniversalSchemaSupport.forSchemaType(schemaType).recordFormatterSupport(kafkaConfig, schemaRegistryClient)
  }.toMap

  def forSchemaType(schemaType: String): RecordFormatterSupport =
    supportBySchemaType.getOrElse(schemaType, throw new UnsupportedSchemaType(schemaType))

}

trait RecordFormatterSupport {
  def formatMessage(data: Any): Json
  def readKeyMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte]
  def readValueMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte]
}

object JsonPayloadRecordFormatterSupport extends RecordFormatterSupport {
  override def formatMessage(data: Any): Json = BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(data)

  override def readKeyMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] = readMessage(topic, schemaOpt, jsonObj)

  override def readValueMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] = readMessage(topic, schemaOpt, jsonObj)

  private def readMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] = jsonObj match {
    // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
    case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
    case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
  }
}

class AvroPayloadRecordFormatterSupport(keyMessageReader: AvroMessageReader, valueMessageReader: AvroMessageReader) extends RecordFormatterSupport {

  override def formatMessage(data: Any): Json = AvroMessageFormatter.asJson(data)

  override def readKeyMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] =
    keyMessageReader.readJson(
      jsonObj,
      schemaOpt.getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None."))
        .asInstanceOf[AvroSchema].rawSchema(), topic)

  override def readValueMessage(topic: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] =
    valueMessageReader.readJson(
      jsonObj,
      schemaOpt.getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None."))
        .asInstanceOf[AvroSchema].rawSchema(), topic)

}