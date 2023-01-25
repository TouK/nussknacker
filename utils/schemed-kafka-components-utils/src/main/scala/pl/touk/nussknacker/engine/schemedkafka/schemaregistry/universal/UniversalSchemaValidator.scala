package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schema.SchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentAvroSchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryError, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.AvroSchemaWithJsonPayload

object UniversalSchemaValidator extends SchemaValidator {

  override def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T] = {
    schema match {
      case s: AvroSchema => ConfluentAvroSchemaValidator.validateSchema[T](schema)
      case s: JsonSchema => Valid(schema)
      case s: AvroSchemaWithJsonPayload => Valid(schema)
      case schema => throw new IllegalArgumentException(s"Unsupported schema type: $schema")
    }
  }

}
