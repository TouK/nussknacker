package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient

import java.nio.charset.StandardCharsets

/**
  * @param schemaRegistryClient schema registry client
  */
private[confluent] class UniversalMessageReader(schemaRegistryClient: SchemaRegistryClient) {

  private val avroMessageReader = new ConfluentAvroMessageReader(schemaRegistryClient)

  def readJson(jsonObj: Json, parsedSchema: ParsedSchema, subject: String): Array[Byte] = {
    parsedSchema match {
      case schema: AvroSchema => avroMessageReader.readJson(jsonObj, schema.rawSchema(), subject)
      //todo: change JsonSchema
      case s: ParsedSchema if s.schemaType() == "JSON" =>
        jsonObj match {
          // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
          case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
          case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
        }
      case s: ParsedSchema => throw new IllegalArgumentException(s"Unsupported schema type: ${s.schemaType()}")
    }
  }

}
