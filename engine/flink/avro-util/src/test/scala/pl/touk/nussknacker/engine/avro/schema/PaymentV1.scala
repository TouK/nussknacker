package pl.touk.nussknacker.engine.avro.schema

object PaymentV1 extends TestSchemaWithRecord {
  val stringSchema: String =
    s"""
       |{
       |  "type": "record",
       |  "name": "Payment",
       |  "fields": [
       |    {
       |      "name": "id",
       |      "type": "string"
       |    },
       |    {
       |      "name": "amount",
       |      "type": "double"
       |    },
       |    {
       |      "name": "currency",
       |      "type": ${Currency.stringSchema}
       |    },
       |    {
       |      "name": "company",
       |      "type": ${Company.stringSchema}
       |    },
       |    {
       |      "name": "products",
       |      "type": {
       |        "type": "array",
       |        "items": ${Product.stringSchema}
       |      }
       |    },
       |    {
       |      "name": "vat",
       |      "type": ["int", "null"]
       |     }
       |   ]
       |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "id" -> "1",
    "amount" -> 100.00,
    "currency" -> Currency.exampleData,
    "company" -> Company.exampleData,
    "products" -> List(
      Product.exampleData,
      Map("id" -> "fff29bd0-0778-4525-83f2-f0e4a486754f", "name" -> "FRAUD", "price" -> 60.00)
    ),
    "vat" -> null
  )

  val exampleDataWithVat: Map[String, Any] = exampleData ++ Map("vat" -> 7)
}
