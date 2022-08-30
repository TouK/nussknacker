package pl.touk.nussknacker.engine.schemedkafka.schema

object NotValidSchema {
  val stringSchema: String =
    """
      |{
      |  "type": "record",
      |  "name": "NotValidSchema",
      |  "fields": [
      |    {
      |      "name": "street"
      |    },
      |    {
      |      "name": "city",
      |      "type": "string"
      |    }
      |  ]
      |}
    """.stripMargin
}
