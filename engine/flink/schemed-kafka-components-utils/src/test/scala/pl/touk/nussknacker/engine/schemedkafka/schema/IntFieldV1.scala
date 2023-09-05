package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object IntFieldV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(100000)

  override def stringSchema: String = """{ "type": "record", "name": "field", "fields": [{"name":"field", "type":"int"}] }"""

  def jsonSchema: String = s"""{"type": "object", "properties": {"field": {"type": "integer", "minimum": ${Int.MinValue}, "maximum": ${Int.MaxValue}}}}"""

  def exampleData(timestamp: Int) = Map("field" -> timestamp)

  def encodeData(timestamp: Int): GenericData.Record = encode(exampleData(timestamp))

}
