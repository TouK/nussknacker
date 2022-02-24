package pl.touk.nussknacker.engine.avro.schema

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
