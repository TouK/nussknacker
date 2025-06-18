package pl.touk.nussknacker.engine.schemedkafka.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

trait TestSchema {
  lazy val schema: Schema              = AvroUtils.parseSchema(stringSchema)
  lazy val confluentSchema: AvroSchema = ConfluentUtils.convertToAvroSchema(schema)
  def stringSchema: String
}

trait TestSchemaWithRecord extends TestSchema {
  final protected val avroEncoder                        = ToAvroSchemaBasedEncoder(ValidationMode.strict)
  def encode(data: Map[String, Any]): GenericData.Record = avroEncoder.encodeRecordOrError(data, schema)
  lazy val record: GenericRecord                         = encode(exampleData)
  def exampleData: Map[String, Any]
}
