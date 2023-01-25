package pl.touk.nussknacker.engine.schemedkafka.schema

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryError

trait SchemaValidator extends Serializable {

  def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T]

}
