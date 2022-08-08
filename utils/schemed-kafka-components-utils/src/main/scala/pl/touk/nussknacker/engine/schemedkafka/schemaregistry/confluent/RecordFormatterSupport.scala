package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.nio.charset.StandardCharsets

trait RecordFormatterSupport {
  def formatMessage(client: SchemaRegistryClient, data: Any): Json
  def readMessage(client: SchemaRegistryClient, subject: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte]
}

object JsonPayloadRecordFormatterSupport extends RecordFormatterSupport {
  override def formatMessage(client: SchemaRegistryClient, data: Any): Json = BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(data)

  override def readMessage(client: SchemaRegistryClient, subject: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] = jsonObj match {
    // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
    case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
    case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
  }
}

object AvroPayloadRecordFromatterSupport extends RecordFormatterSupport {

  override def formatMessage(client: SchemaRegistryClient, data: Any): Json = new ConfluentAvroMessageFormatter(client).asJson(data)

  override def readMessage(client: SchemaRegistryClient, subject: String, schemaOpt: Option[ParsedSchema], jsonObj: Json): Array[Byte] =
    new ConfluentAvroMessageReader(client)
      .readJson(jsonObj,
      schemaOpt.getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None."))
        .asInstanceOf[AvroSchema].rawSchema(), subject)
}