package pl.touk.nussknacker.engine.schemedkafka.schema

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryError

object AvroSchemaValidator extends SchemaValidator {
  override def validateSchema(schema: ParsedSchema): ValidatedNel[SchemaRegistryError, Unit] = {
    schema match {
      case _: AvroSchema => Valid(())
      case schema => throw new IllegalArgumentException(s"Unsupported schema type: $schema")
    }
  }
}
