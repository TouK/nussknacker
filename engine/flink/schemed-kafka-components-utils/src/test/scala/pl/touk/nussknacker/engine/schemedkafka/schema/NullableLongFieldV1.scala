package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object NullableLongFieldV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(Some(100000))

  override def stringSchema: String = """{ "type": "record", "name": "longField", "fields": [{"name":"field", "type":["null", "long"]}] }"""

  def exampleData(timestamp: Option[Long]) = Map("field" -> timestamp.map[java.lang.Long](_.longValue()).orNull)

  def encodeData(timestamp: Option[Long]): GenericData.Record = encode(exampleData(timestamp))

}
