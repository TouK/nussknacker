package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError

trait AvroSchemaDeterminer extends Serializable {

  def determineSchemaUsedInTyping: Validated[SchemaRegistryError, Schema]

  def determineSchemaInRuntime: Validated[SchemaRegistryError, Option[Schema]]

}
