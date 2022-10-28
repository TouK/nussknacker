package pl.touk.nussknacker.engine.schemedkafka.schema

object GeneratedAvroClassWithLogicalTypesNewSchema extends TestSchema {

  override def stringSchema: String =
    """{
      |  "type": "record",
      |  "name": "GeneratedAvroClassWithLogicalTypes",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka.schema",
      |  "fields": [
      |    {
      |      "name": "dateTime",
      |      "type": [
      |        "null",
      |        {
      |          "type": "long",
      |          "logicalType": "timestamp-millis"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "date",
      |      "type": [
      |        "null",
      |        {
      |          "type": "int",
      |          "logicalType": "date"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "time",
      |      "type": [
      |        "null",
      |        {
      |          "type": "int",
      |          "logicalType": "time-millis"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "text2",
      |      "type": "string"
      |    },
      |    {
      |      "name": "decimal",
      |      "type": [
      |        "null",
      |        {
      |          "type": "bytes",
      |          "logicalType": "decimal",
      |          "precision": 4,
      |          "scale": 2
      |        }
      |      ],
      |      "default": null
      |    }
      |  ]
      |}""".stripMargin

}

