package pl.touk.nussknacker.engine.schemedkafka.schema

object Currency extends TestSchema {
  val stringSchema: String =
    """
      |{
      |  "type": "enum",
      |  "name": "Currency",
      |  "symbols": [
      |    "PLN",
      |    "EUR",
      |    "GBP",
      |    "USD"
      |  ]
      |}
    """.stripMargin

  val exampleData: String = "PLN"
}
