package pl.touk.nussknacker.engine.schemedkafka.schema

object AllTypesAvroSchemaWithoutDefaultValues extends TestSchema {

  override val stringSchema: String = {
    s"""
       |{
       |  "type": "record",
       |  "name": "AllTypesTest",
       |  "namespace": "com.example",
       |  "fields": [
       |    {
       |      "name": "stringField",
       |      "type": "string"
       |    },
       |    {
       |      "name": "intField",
       |      "type": "int"
       |    },
       |    {
       |      "name": "longField",
       |      "type": "long"
       |    },
       |    {
       |      "name": "floatField",
       |      "type": "float"
       |    },
       |    {
       |      "name": "doubleField",
       |      "type": "double"
       |    },
       |    {
       |      "name": "booleanField",
       |      "type": "boolean"
       |    },
       |    {
       |      "name": "nullField",
       |      "type": "null"
       |    },
       |    {
       |      "name": "enumField",
       |      "type": {
       |        "type": "enum",
       |        "name": "Color",
       |        "symbols": [
       |          "RED",
       |          "GREEN",
       |          "BLUE"
       |        ]
       |      }
       |    },
       |    {
       |      "name": "arrayField",
       |      "type": {
       |        "type": "array",
       |        "items": "string"
       |      }
       |    },
       |    {
       |      "name": "mapField",
       |      "type": {
       |        "type": "map",
       |        "values": "int"
       |      }
       |    },
       |    {
       |      "name": "fixedField",
       |      "type": {
       |        "type": "fixed",
       |        "name": "MD5",
       |        "size": 16
       |      }
       |    },
       |    {
       |      "name": "bytesField",
       |      "type": "bytes"
       |    },
       |    {
       |      "name": "unionField",
       |      "type": [
       |        "null",
       |        "string",
       |        "int"
       |      ]
       |    },
       |    {
       |      "name": "recordField",
       |      "type": {
       |        "type": "record",
       |        "name": "NestedRecord",
       |        "fields": [
       |          {
       |            "name": "nestedString",
       |            "type": "string"
       |          }
       |        ]
       |      }
       |    },
       |    {
       |      "name": "logicalDate",
       |      "type": {
       |        "type": "int",
       |        "logicalType": "date"
       |      }
       |    },
       |    {
       |      "name": "logicalTimestamp",
       |      "type": {
       |        "type": "long",
       |        "logicalType": "timestamp-millis"
       |      }
       |    }
       |  ]
       |}""".stripMargin
  }

}
