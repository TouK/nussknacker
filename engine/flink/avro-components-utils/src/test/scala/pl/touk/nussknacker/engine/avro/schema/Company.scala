package pl.touk.nussknacker.engine.avro.schema

object Company extends TestSchemaWithRecord {

  final val DefaultName = "TOUK SP Z O O SPÓŁKA KOMANDYTOWO AKCYJNA"

  val stringSchema: String =
    s"""
      |{
      |  "type": "record",
      |  "name": "Company",
      |  "fields": [
      |    {
      |      "name": "name",
      |      "type": "string"
      |    },
      |    {
      |      "name": "address",
      |      "type": ${Address.stringSchema}
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "name" -> DefaultName,
    "address" -> Address.exampleData
  )

  val jsonMap: String =
    s"""
      |{
      |   name: "$DefaultName",
      |   address: ${Address.jsonMap}
      |}
     """.stripMargin
}
