package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.specific.SpecificRecordBase
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}

trait TestSchema {
  lazy val schema: Schema = AvroUtils.parseSchema(stringSchema)
  def stringSchema: String
}

trait TestSchemaWithRecord extends TestSchema {
  final protected val avroEncoder = BestEffortAvroEncoder(ValidationMode.strict)
  def encode(data: Map[String, Any]): GenericData.Record = avroEncoder.encodeRecordOrError(data, schema)
  lazy val record: GenericRecord = encode(exampleData)
  def exampleData: Map[String, Any]
}

trait TestSchemaWithSpecificRecord extends TestSchemaWithRecord {
  def specificRecord: SpecificRecordBase
}
