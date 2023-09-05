package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object NullableDateFieldV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(1000)

  override def stringSchema: String = """{ "type": "record", "name": "field", "fields": [{"name":"field", "type": ["null", {"type": "int", "logicalType": "date"}]}]}"""

  def exampleData(daysSinceEpoch: Int) = Map("field" -> daysSinceEpoch)

  def encodeData(daysSinceEpoch: Int): GenericData.Record = encode(exampleData(daysSinceEpoch))

}
