package pl.touk.nussknacker.engine.avro.schema

object Address extends TestSchemaWithRecord {
  final val DefaultCity = "Warsaw"
  final val DefaultStreet = "ul. Bohaterów Września 9"

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
    "city" -> DefaultCity,
    "street" -> DefaultStreet
  )

  val jsonMap: String =
    s"""
       |{
       |  city: "$DefaultCity",
       |  street: "$DefaultStreet"
       |}
     """.stripMargin
}
