package pl.touk.nussknacker.engine.avro.schema

object Product extends TestSchemaWithRecord {
  val stringSchema: String =
    """
      |{
      |  "type": "record",
      |  "name": "Product",
      |  "fields": [
      |    {
      |      "name": "id",
      |      "type": "string"
      |    },
      |    {
      |      "name": "name",
      |      "type": "string"
      |    },
      |    {
      |      "name": "price",
      |      "type": "double"
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "id" -> "11b682af-4b37-45d9-8b47-f11d04213ecf", "name" -> "RTM", "price" -> 40.00
  )
}
