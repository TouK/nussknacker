package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder

trait TestSchema {
  lazy val schema: Schema = AvroUtils.parseSchema(stringSchema)
  def stringSchema: String
}

trait TestSchemaWithRecord extends TestSchema {
  final protected val avroEncoder                        = ToAvroSchemaBasedEncoder(ValidationMode.strict)
  def encode(data: Map[String, Any]): GenericData.Record = avroEncoder.encodeRecordOrError(data, schema)
  lazy val record: GenericRecord                         = encode(exampleData)
  def exampleData: Map[String, Any]
}
