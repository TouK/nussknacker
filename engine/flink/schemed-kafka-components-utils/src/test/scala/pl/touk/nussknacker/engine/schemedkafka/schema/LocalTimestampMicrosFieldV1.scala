package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object LocalTimestampMicrosFieldV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(100000)

  override def stringSchema: String = """{ "type": "record", "name": "field", "fields": [{"name":"field", "type":{"type": "long","logicalType": "local-timestamp-micros"}}] }"""

  def exampleData(timestamp: Long) = Map("field" -> timestamp)

  def encodeData(timestamp: Long): GenericData.Record = encode(exampleData(timestamp))

}
