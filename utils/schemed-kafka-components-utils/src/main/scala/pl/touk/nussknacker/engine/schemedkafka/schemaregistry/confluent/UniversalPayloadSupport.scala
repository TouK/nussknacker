package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.nio.charset.StandardCharsets

trait UniversalPayloadSupport {
  def messageFormatter(client: SchemaRegistryClient): Any => Json
  def messageReader(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient): (Json, String) => Array[Byte]
}

object PayloadType extends Enumeration {
  type PayloadType = Value
  val Avro, Json = Value
}

object JsonPayloadSupport extends UniversalPayloadSupport {
  override def messageFormatter(client: SchemaRegistryClient): Any => Json =
    (obj: Any) => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)


  override def messageReader(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient): (Json, String) => Array[Byte] =
    (jsonObj, _) => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
}

object AvroPayloadSupport extends UniversalPayloadSupport {
  override def messageFormatter(client: SchemaRegistryClient): Any => Json = (obj: Any) =>
    new ConfluentAvroMessageFormatter(client).asJson(obj)

  override def messageReader(schemaOpt: Option[ParsedSchema], client: SchemaRegistryClient): (Json, String) => Array[Byte] =
    (jsonObj, subject) => new ConfluentAvroMessageReader(client).readJson(jsonObj, schemaOpt.getOrElse(throw new IllegalArgumentException("Schema is required for Avro message reader, but got None.")).cast().rawSchema(), subject)

}