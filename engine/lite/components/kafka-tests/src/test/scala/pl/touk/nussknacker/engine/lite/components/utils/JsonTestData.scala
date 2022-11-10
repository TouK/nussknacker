package pl.touk.nussknacker.engine.lite.components.utils

import io.circe.Json
import io.circe.Json.{fromString, obj}
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

object JsonTestData {

  val ObjectFieldName: String = "field"

  val InputEmptyObject = "{}"

  val integerRangeSchema: Schema = SchemaLoader.load(new JSONObject(
    s"""{
       |  "$$schema": "https://json-schema.org/draft-07/schema",
       |  "type": "integer",
       |  "minimum": ${Integer.MIN_VALUE},
       |  "maximum": ${Integer.MAX_VALUE}
       |}""".stripMargin))

  val longSchema: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "$schema": "https://json-schema.org/draft-07/schema",
      |  "type": "integer"
      |}""".stripMargin))

  val objectSchema: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" :  {
      |      "type": "object",
      |      "properties": {
      |        "first" : { "type": "string" },
      |        "last" : { "type": "string" }
      |      },
      |      "required": ["first", "last"]
      |    }
      |  }
      |}
      |""".stripMargin))

  val objectSchemaWithNull: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "type": "object",
      |  "properties": {
      |    "first" : { "type": "string" },
      |    "nullField" : { "type": "null" }
      |   },
      |   "required": ["first"]
      |}
      |""".stripMargin))

  val sampleDataWithNull: Json = obj("first" -> fromString("lcl"), "nullField" -> Json.Null)

  val personSchema: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "$schema": "https://json-schema.org/draft-07/schema",
      |  "type": "object",
      |  "properties": {
      |    "first": {
      |      "type": "string"
      |    },
      |    "last": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "type": "integer"
      |    }
      |  }
      |}""".stripMargin))

  val schemaObjString: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": "string" }
      |   }
      |}
      |""".stripMargin))

  val schemaObjNull: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": "null" }
      |   }
      |}
      |""".stripMargin))

  val schemaObjUnionNullString: Schema = SchemaLoader.load(new JSONObject(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": ["null", "string"] }
      |   }
      |}
      |""".stripMargin))
}
