package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

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

  val exampleData: Map[String, Any] = PaymentV1.exampleData ++ Map("attributes" -> Map(), "cnt" -> 0)

  val exampleDataWithAttributes: Map[String, Any] = PaymentV1.exampleDataWithVat ++ Map(
    "cnt" -> 1,
    "attributes" -> Map(
      "partner" -> "true"
    )
  )

  val recordWithData: GenericData.Record = avroEncoder.encodeRecordOrError(exampleDataWithAttributes, schema)

  val jsonMap: String =
    s"""{
       |  id: #input.id,
       |  amount: #input.amount,
       |  currency: "${Currency.exampleData}",
       |  company: {
       |    name: #input.company.name,
       |    address: {
       |      city: #input.company.address.city,
       |      street: #input.company.address.street
       |    }
       |  },
       |  products: {
       |    {id: #input.products[0].id, name: #input.products[0].name, price: #input.products[0].price},
       |    {id: #input.products[1].id, name: #input.products[1].name, price: #input.products[1].price}
       |  },
       |  vat: #input.vat,
       |  cnt: 0,
       |  attributes: {:}
       |}""".stripMargin
}
