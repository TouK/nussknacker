package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaValidator, SchemaValidator}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.AvroSchemaWithJsonPayload

object UniversalSchemaValidator extends SchemaValidator {

  override def validateSchema(schema: ParsedSchema): ValidatedNel[SchemaRegistryError, Unit] = {
    schema match {
      case s: AvroSchema => AvroSchemaValidator.validateSchema(schema)
      case s: JsonSchema => Valid(())
      case s: AvroSchemaWithJsonPayload => Valid(())
      case schema => throw new IllegalArgumentException(s"Unsupported schema type: $schema")
    }
  }

}
