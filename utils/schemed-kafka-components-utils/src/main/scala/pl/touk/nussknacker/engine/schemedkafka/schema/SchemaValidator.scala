package pl.touk.nussknacker.engine.schemedkafka.schema

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryError

trait SchemaValidator extends Serializable {

  def validateSchema(schema: ParsedSchema): ValidatedNel[SchemaRegistryError, Unit]

}
