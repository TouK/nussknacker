package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient

/**
 * @param schemaRegistryClient schema registry client
 */
private[confluent] class UniversalMessageFormatter(schemaRegistryClient: SchemaRegistryClient) {

  private lazy val avroMessageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient)

  def asJson(obj: Any, parsedSchema: ParsedSchema): Json = parsedSchema match {
    case _: AvroSchema => avroMessageFormatter.asJson(obj)
    //todo: change to JsonSchema
    case s: ParsedSchema if s.schemaType() == "JSON" => ??? //todo: handle json schema
    case s: ParsedSchema  => throw new IllegalArgumentException(s"Unsupported schema type: ${s.schemaType()}")
  }
}
