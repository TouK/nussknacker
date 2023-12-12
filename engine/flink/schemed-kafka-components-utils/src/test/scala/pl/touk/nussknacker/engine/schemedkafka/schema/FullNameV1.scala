package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object FullNameV1 extends TestSchemaWithRecord {
  final val BaseFirst = "Lucas"
  final val BaseLast  = "Touk"

  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka.schema",
      |  "name": "FullNameV1",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map("first" -> BaseFirst, "last" -> BaseLast)

  def createRecord(first: String, last: String): GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> first, "last" -> last), schema)

}
