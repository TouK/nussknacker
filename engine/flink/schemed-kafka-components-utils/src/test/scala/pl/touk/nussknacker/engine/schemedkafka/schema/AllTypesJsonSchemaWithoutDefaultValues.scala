package pl.touk.nussknacker.engine.schemedkafka.schema

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

object AllTypesJsonSchemaWithoutDefaultValues {

  val schema: Schema = JsonSchemaBuilder.parseSchema(
    s"""
       |{
       |  "$$schema": "https://json-schema.org/draft/2020-12/schema",
       |  "$$id": "https://example.com/schemas/AllTypesTest.json",
       |  "title": "AllTypesTest",
       |  "type": "object",
       |  "properties": {
       |    "stringField": {
       |      "type": "string"
       |    },
       |    "intField": {
       |      "type": "integer"
       |    },
       |    "longField": {
       |      "type": "integer"
       |    },
       |    "floatField": {
       |      "type": "number"
       |    },
       |    "doubleField": {
       |      "type": "number"
       |    },
       |    "booleanField": {
       |      "type": "boolean"
       |    },
       |    "nullField": {
       |      "type": "null"
       |    },
       |    "enumField": {
       |      "type": "string",
       |      "enum": ["RED", "GREEN", "BLUE"]
       |    },
       |    "arrayField": {
       |      "type": "array",
       |      "items": {
       |        "type": "string"
       |      }
       |    },
       |    "mapField": {
       |      "type": "object",
       |      "additionalProperties": {
       |        "type": "integer"
       |      }
       |    },
       |    "fixedField": {
       |      "type": "string",
       |      "contentEncoding": "base64",
       |      "minLength": 24,
       |      "maxLength": 24
       |    },
       |    "bytesField": {
       |      "type": "string",
       |      "contentEncoding": "base64"
       |    },
       |    "unionField": {
       |      "anyOf": [
       |        { "type": "string" },
       |        { "type": "null" },
       |        { "type": "integer" },
       |        { "type": "boolean" },
       |        { "type": "object" }
       |      ]
       |    },
       |    "recordField": {
       |      "type": "object",
       |      "properties": {
       |        "nestedString": {
       |          "type": "string"
       |        }
       |      },
       |      "required": ["nestedString"]
       |    },
       |    "logicalDate": {
       |      "type": "string",
       |      "format": "date"
       |    },
       |    "logicalTimestamp": {
       |      "type": "string",
       |      "format": "date-time"
       |    }
       |  },
       |  "required": [
       |    "stringField", "intField", "longField", "floatField", "doubleField",
       |    "booleanField", "nullField", "enumField", "arrayField", "mapField",
       |    "fixedField", "bytesField", "unionField", "recordField",
       |    "logicalDate", "logicalTimestamp"
       |  ]
       |}
       |""".stripMargin
  )

}
