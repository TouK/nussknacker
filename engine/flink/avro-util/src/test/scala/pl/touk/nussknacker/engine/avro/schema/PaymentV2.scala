package pl.touk.nussknacker.engine.avro.schema

object PaymentV2 extends TestSchemaWithRecord {
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
       |    },
       |    {
       |      "name": "cnt",
       |      "type": ["int", "null"],
       |      "default": 0
       |    },
       |    {
       |      "name": "attributes",
       |      "type":[{
       |        "type": "map",
       |        "values": "string"
       |      }, "null"],
       |      "default": {}
       |    }
       |   ]
       |}
    """.stripMargin

  val exampleData: Map[String, Any] = PaymentV1.exampleData ++ Map("attributes" -> Map())

  val exampleDataWithAttributes: Map[String, Any] = PaymentV1.exampleDataWithVat ++ Map(
    "attributes" -> Map(
      "partner" -> "true"
    )
  )
}
