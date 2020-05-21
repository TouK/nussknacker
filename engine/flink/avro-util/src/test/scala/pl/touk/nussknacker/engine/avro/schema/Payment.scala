package pl.touk.nussknacker.engine.avro.schema

object Company extends TestSchemaWithRecord {
  val stringSchema: String =
    s"""
      |{
      |  "type": "record",
      |  "name": "Company",
      |  "fields": [
      |    {
      |      "name": "name",
      |      "type": "string"
      |    },
      |    {
      |      "name": "address",
      |      "type": ${Address.stringSchema}
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map(
    "name" -> "TOUK SP Z O O SPÓŁKA KOMANDYTOWO AKCYJNA",
    "address" -> Address.exampleData
  )
}

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
      |      "name": "attributes",
      |      "type": {
      |        "type": "map",
      |        "values": "string"
      |      },
      |      "defaults": {}
      |    }
      |   ]
      |}
    """.stripMargin

  val exampleData = Map(
    "id" -> "1",
    "amount" -> 100.00,
    "currency" -> Currency.exampleData,
    "company" -> Company.exampleData,
    "products" -> List(
      Product.exampleData,
      Map("id" -> "fff29bd0-0778-4525-83f2-f0e4a486754f", "name" -> "FRAUD", "price" -> 60.00)
    ),
    "attributes" -> Map(
      "partner" -> "true",
      "cnt" -> "1024"
    )
  )
}
