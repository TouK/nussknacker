package pl.touk.nussknacker.engine.schemedkafka.schema

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

object AllTypesJsonSchemaWithDefaultValues {

  val schema: Schema = JsonSchemaBuilder.parseSchema(
    s"""
       |{
       |  "$$schema": "https://json-schema.org/draft/2020-12/schema",
       |  "$$id": "https://example.com/schemas/AllTypesTest.json",
       |  "title": "AllTypesTest",
       |  "type": "object",
       |  "properties": {
       |    "stringField": {
       |      "type": "string",
       |      "default": "example"
       |    },
       |    "intField": {
       |      "type": "integer",
       |      "default": 42
       |    },
       |    "longField": {
       |      "type": "integer",
       |      "default": 1234567890
       |    },
       |    "floatField": {
       |      "type": "number",
       |      "default": 3.14
       |    },
       |    "doubleField": {
       |      "type": "number",
       |      "default": 2.71828
       |    },
       |    "booleanField": {
       |      "type": "boolean",
       |      "default": true
       |    },
       |    "nullField": {
       |      "type": "null",
       |      "default": null
       |    },
       |    "enumField": {
       |      "type": "string",
       |      "enum": ["RED", "GREEN", "BLUE"],
       |      "default": "RED"
       |    },
       |    "arrayField": {
       |      "type": "array",
       |      "items": {
       |        "type": "string"
       |      },
       |      "default": ["item1", "item2"]
       |    },
       |    "mapField": {
       |      "type": "object",
       |      "additionalProperties": {
       |        "type": "integer"
       |      },
       |      "default": {
       |        "a": 1,
       |        "b": 2
       |      }
       |    },
       |    "fixedField": {
       |      "type": "string",
       |      "contentEncoding": "base64",
       |      "minLength": 24,
       |      "maxLength": 24,
       |      "default": "AAAAAAAAAAAAAAAAAAAAAA=="
       |    },
       |    "bytesField": {
       |      "type": "string",
       |      "contentEncoding": "base64",
       |      "default": "AQID"
       |    },
       |    "unionField": {
       |      "anyOf": [
       |        { "type": "null" },
       |        { "type": "string" },
       |        { "type": "integer" }
       |      ],
       |      "default": null
       |    },
       |    "recordField": {
       |      "type": "object",
       |      "properties": {
       |        "nestedString": {
       |          "type": "string",
       |          "default": "inside"
       |        }
       |      },
       |      "required": ["nestedString"],
       |      "default": {
       |        "nestedString": "inside"
       |      }
       |    },
       |    "logicalDate": {
       |      "type": "string",
       |      "format": "date",
       |      "default": "2022-01-01"
       |    },
       |    "logicalTimestamp": {
       |      "type": "string",
       |      "format": "date-time",
       |      "default": "2023-03-28T12:00:00Z"
       |    }
       |  },
       |  "required": [
       |    "stringField", "intField", "longField", "floatField", "doubleField",
       |    "booleanField", "nullField", "enumField", "arrayField", "mapField",
       |    "fixedField", "bytesField", "unionField", "recordField",
       |    "logicalDate", "logicalTimestamp"
       |  ]
       |}
       |
       |
       |""".stripMargin
  )

}
