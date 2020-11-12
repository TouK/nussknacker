package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.specific.SpecificRecord
import org.apache.flink.formats.avro.typeutils.LogicalTypesAvroFactory
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, AvroUtils, SchemaDeterminerError, SchemaWithId}

class SpecificRecordEmbeddedSchemaDeterminer(clazz: Class[_ <: SpecificRecord]) extends AvroSchemaDeterminer {

  // schema id is needed for GenericRecord serialization tweak, so in SpecificRecord case we can pass here None
  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, SchemaWithId] =
    Valid(SchemaWithId(LogicalTypesAvroFactory.extractAvroSpecificSchema(clazz, AvroUtils.specificData), None))

}
