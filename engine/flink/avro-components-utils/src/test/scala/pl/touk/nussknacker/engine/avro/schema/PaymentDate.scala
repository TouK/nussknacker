package pl.touk.nussknacker.engine.avro.schema

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

import org.apache.avro.generic.GenericData

object PaymentDate extends TestSchemaWithRecord {

  val date = LocalDateTime.of(2020, 1, 2, 3, 14, 15)

  val instant = date.toInstant(ZoneOffset.UTC)

  val decimal = 12.34

  val uuid = UUID.randomUUID()

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
       |      "name": "dateTime",
       |      "type": {
       |        "type": "long",
       |        "logicalType": "timestamp-millis"
       |      }
       |    },
       |    {
       |      "name": "date",
       |      "type": {
       |        "type": "int",
       |        "logicalType": "date"
       |      }
       |    },
       |    {
       |      "name": "time",
       |      "type": {
       |        "type": "int",
       |        "logicalType": "time-millis"
       |      }
       |    },
       |    {
       |      "name": "decimal",
       |      "type": {
       |        "type": "bytes",
       |        "logicalType": "decimal",
       |        "precision": 4,
       |        "scale": 2
       |      }
       |    },
       |    {
       |      "name": "uuid",
       |      "type": {
       |        "type": "string",
       |        "logicalType": "uuid"
       |      }
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
    "dateTime" -> instant,
    "date" -> date.toLocalDate,
    "time" -> date.toLocalTime,
    "decimal" -> decimal,
    "uuid" -> uuid,
    "currency" -> Currency.exampleData,
    "company" -> Company.exampleData,
    "products" -> List(
      Product.exampleData,
      Map("id" -> "fff29bd0-0778-4525-83f2-f0e4a486754f", "name" -> "FRAUD", "price" -> 60.00)
    ),
    "vat" -> null
  )

  val recordWithData: GenericData.Record = avroEncoder.encodeRecordOrError(exampleData, schema)

}
