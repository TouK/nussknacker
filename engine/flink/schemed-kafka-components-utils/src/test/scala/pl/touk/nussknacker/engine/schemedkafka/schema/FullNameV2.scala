package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.generic.GenericData

object FullNameV2 extends TestSchemaWithRecord {
  final val BaseMiddle = "SP"

  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka.schema",
      |  "name": "FullNameV2",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = FullNameV1.exampleData ++ Map("middle" -> BaseMiddle)

  def createRecord(first: String, middle: String, last: String): GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> first, "last" -> last, "middle" -> middle), schema)

  def migratedGenericRecordFromV1: GenericData.Record =
    avroEncoder.encodeRecordOrError(
      Map("first" -> FullNameV1.BaseFirst, "last" -> FullNameV1.BaseLast, "middle" -> null),
      schema
    )

}
