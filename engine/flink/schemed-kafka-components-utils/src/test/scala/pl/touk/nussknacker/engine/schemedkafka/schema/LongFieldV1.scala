package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object LongFieldV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(100000)

  override def stringSchema: String = """{ "type": "record", "name": "longField", "fields": [{"name":"field", "type":"long"}] }"""

  def jsonSchema: String = """{"type": "object", "properties": {"field": {"type": "integer"}}}"""

  def exampleData(timestamp: Long) = Map("field" -> timestamp)

  def encodeData(timestamp: Long): GenericData.Record = encode(exampleData(timestamp))

}
