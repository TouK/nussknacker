package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.schemedkafka.schema.SchemaValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryError, SchemaRegistryUnsupportedTypeError}

object ConfluentAvroSchemaValidator extends SchemaValidator {
  override def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T] = {
    schema match {
      case s: AvroSchema => {
        /* kafka-avro-serializer does not support Array at top level
        [https://github.com/confluentinc/schema-registry/issues/1298] */
        if (s.rawSchema().getType == Schema.Type.ARRAY)
          Invalid(NonEmptyList.of(
            SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
        else
          Valid(schema)
      }
      case schema => throw new IllegalArgumentException(s"Unsupported schema type: $schema")
    }
  }
}
