package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.Schema
import org.apache.avro.specific.SpecificRecord
import org.apache.flink.formats.avro.typeutils.LogicalTypesAvroFactory
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, AvroUtils}

class SpecificRecordEmbeddedSchemaDeterminer(clazz: Class[_ <: SpecificRecord]) extends AvroSchemaDeterminer {

  override def determineSchemaInRuntime: Validated[SchemaRegistryError, Option[Schema]] =
    determineSchemaUsedInTyping.map(Some(_))

  override def determineSchemaUsedInTyping: Validated[SchemaRegistryError, Schema] =
    Valid(LogicalTypesAvroFactory.extractAvroSpecificSchema(clazz, AvroUtils.specificData))

}
