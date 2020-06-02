package pl.touk.nussknacker.engine.avro.schema

object Address extends TestSchemaWithRecord {
  val stringSchema: String =
    """
      |{
      |  "type": "record",
      |  "name": "Address",
      |  "fields": [
      |    {
      |      "name": "street",
      |      "type": "string"
      |    },
      |    {
      |      "name": "city",
      |      "type": "string"
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "city" -> "Warsaw",
    "street" -> "ul. Bohaterów Września 9"
  )
}
