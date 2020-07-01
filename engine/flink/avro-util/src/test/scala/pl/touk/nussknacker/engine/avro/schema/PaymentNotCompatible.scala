package pl.touk.nussknacker.engine.avro.schema

object PaymentNotCompatible extends TestSchemaWithRecord {
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
       |    },
       |    {
       |      "name": "date",
       |      "type": ["int"]
       |    }
       |   ]
       |}
    """.stripMargin

  val exampleData = PaymentV2.exampleData ++ Map("attributes" -> Map(), "date" -> 189123)
}
