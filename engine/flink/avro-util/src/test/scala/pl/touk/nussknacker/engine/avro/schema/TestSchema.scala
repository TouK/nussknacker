package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.specific.SpecificRecordBase
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

trait TestSchema {
  lazy val schema: Schema = AvroUtils.parseSchema(stringSchema)
  def stringSchema: String
  def exampleData: Any
}

trait TestSchemaWithRecord extends TestSchema {
  lazy val record: GenericRecord = BestEffortAvroEncoder.encodeRecordOrError(exampleData, schema)
  def exampleData: Map[String, Any]
}

trait TestSchemaWithSpecificRecord extends TestSchemaWithRecord {
  def specificRecord: SpecificRecordBase
}
