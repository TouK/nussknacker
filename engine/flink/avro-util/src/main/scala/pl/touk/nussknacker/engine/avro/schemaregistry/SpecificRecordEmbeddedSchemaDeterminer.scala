package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.specific.SpecificRecord
import org.apache.flink.formats.avro.typeutils.LogicalTypesAvroFactory
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, AvroUtils, SchemaDeterminerError, RuntimeSchemaData}

class SpecificRecordEmbeddedSchemaDeterminer(clazz: Class[_ <: SpecificRecord]) extends AvroSchemaDeterminer {

  // Schema id is needed for GenericRecord serialization tweak, so in SpecificRecord case we can pass here None
  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData] =
    Valid(RuntimeSchemaData(LogicalTypesAvroFactory.extractAvroSpecificSchema(clazz, AvroUtils.specificData), None))

}
