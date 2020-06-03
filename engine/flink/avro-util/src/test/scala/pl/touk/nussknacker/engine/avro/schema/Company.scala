package pl.touk.nussknacker.engine.avro.schema

object Company extends TestSchemaWithRecord {
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
    "name" -> "TOUK SP Z O O SPÓŁKA KOMANDYTOWO AKCYJNA",
    "address" -> Address.exampleData
  )
}
