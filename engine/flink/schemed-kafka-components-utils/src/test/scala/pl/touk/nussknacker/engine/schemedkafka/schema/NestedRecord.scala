package pl.touk.nussknacker.engine.schemedkafka.schema

object NestedRecord extends TestSchemaWithRecord {

  val stringSchema: String =
    """
      |{
      |  "type": "record",
      |  "name": "gskTest",
      |  "fields": [
      |    {
      |      "name": "mapSimple",
      |      "type": [
      |        "null",
      |        {
      |          "type": "record",
      |          "name": "messageSimple",
      |          "fields": [
      |            {
      |              "name": "id",
      |              "type": [
      |                "null",
      |                "string"
      |              ],
      |              "default": null
      |            }
      |          ]
      |        }
      |      ],
      |      "default": null
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "mapSimple" -> Map("id" -> "id"),
  )

  val jsonMap: String =
    s"""
       |{
       |  mapSimple: {id: "id"}
       |}
     """.stripMargin

}
