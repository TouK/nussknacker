package pl.touk.nussknacker.engine.schemedkafka.schema

object AllTypesAvroSchemaWithDefaultValues extends TestSchema {

  override val stringSchema: String = {
    s"""
       |{
       |  "type": "record",
       |  "name": "AllTypesTest",
       |  "namespace": "com.example",
       |  "fields": [
       |    {
       |      "name": "stringField",
       |      "type": "string",
       |      "default": "example"
       |    },
       |    {
       |      "name": "intField",
       |      "type": "int",
       |      "default": 42
       |    },
       |    {
       |      "name": "longField",
       |      "type": "long",
       |      "default": 1234567890
       |    },
       |    {
       |      "name": "floatField",
       |      "type": "float",
       |      "default": 3.14
       |    },
       |    {
       |      "name": "doubleField",
       |      "type": "double",
       |      "default": 2.71828
       |    },
       |    {
       |      "name": "booleanField",
       |      "type": "boolean",
       |      "default": true
       |    },
       |    {
       |      "name": "nullField",
       |      "type": "null",
       |      "default": null
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
       |      },
       |      "default": "BLUE"
       |    },
       |    {
       |      "name": "arrayField",
       |      "type": {
       |        "type": "array",
       |        "items": "string"
       |      },
       |      "default": [
       |        "item1",
       |        "item2"
       |      ]
       |    },
       |    {
       |      "name": "mapField",
       |      "type": {
       |        "type": "map",
       |        "values": "int"
       |      },
       |      "default": {
       |        "a": 1,
       |        "b": 2
       |      }
       |    },
       |    {
       |      "name": "fixedField",
       |      "type": {
       |        "type": "fixed",
       |        "name": "MD5",
       |        "size": 16
       |      },
       |      "default": "\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000"
       |    },
       |    {
       |      "name": "bytesField",
       |      "type": "bytes",
       |      "default": "\\u0001\\u0002\\u0003"
       |    },
       |    {
       |      "name": "unionField",
       |      "type": [
       |        "null",
       |        "string",
       |        "int"
       |      ],
       |      "default": null
       |    },
       |    {
       |      "name": "recordField",
       |      "type": {
       |        "type": "record",
       |        "name": "NestedRecord",
       |        "fields": [
       |          {
       |            "name": "nestedString",
       |            "type": "string",
       |            "default": "inside"
       |          }
       |        ]
       |      },
       |      "default": {
       |        "nestedString": "inside"
       |      }
       |    },
       |    {
       |      "name": "logicalDate",
       |      "type": {
       |        "type": "int",
       |        "logicalType": "date"
       |      },
       |      "default": 19000
       |    },
       |    {
       |      "name": "logicalTimestamp",
       |      "type": {
       |        "type": "long",
       |        "logicalType": "timestamp-millis"
       |      },
       |      "default": 1680000000000
       |    }
       |  ]
       |}""".stripMargin
  }

}
