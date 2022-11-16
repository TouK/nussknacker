package pl.touk.nussknacker.engine.lite.components.utils

import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.test.SpecialSpELElement
import io.circe.Json._
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

import java.time.Year

object JsonTestData {

  val ObjectFieldName: String = "field"

  val InputEmptyObject = "{}"

  val OutputField: SpecialSpELElement = SpecialSpELElement(s"#input.$ObjectFieldName")

  val personSchema: Schema = JsonSchemaBuilder.parseSchema(
    """{
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
      |  },
      |  "additionalProperties": false
      |}""".stripMargin)

  val schemaInteger: Schema = JsonSchemaBuilder.parseSchema("""{"type": "integer"}""")

  val schemaIntegerRange: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
       |  "type": "integer",
       |  "minimum": ${Integer.MIN_VALUE},
       |  "maximum": ${Integer.MAX_VALUE}
       |}""".stripMargin)

  val schemaObjFirstLastName: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" :  {
      |      "type": "object",
      |      "properties": {
      |        "first" : { "type": "string" },
      |        "last" : { "type": "string" }
      |      },
      |      "required": ["first", "last"],
      |      "additionalProperties": false
      |    }
      |  },
      |  "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjAdditionalProperties: Schema = JsonSchemaBuilder.parseSchema(
    """{"type": "object", "additionalProperties": true}""".stripMargin
  )

  val schemaObjInteger: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": "integer" }
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": "string" }
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjNull: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": "null" }
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": ["null", "string"] }
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)
  
  val schemaObjRequiredUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field" : { "type": ["null", "string"] }
      |   },
      |   "required": ["field"],
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val sampleInteger: Json = fromInt(1)

  val sampleObjFirstLastName: Json = obj("first" -> fromString("Nu"), "last" -> fromString("TouK"))

  val sampleSpELFirstLastName: Map[String, Any] = Map("first" -> "Nu", "last" -> "TouK")

  val sampleObjWithAdditional: Json = obj("first" -> fromString("Nu"), "year" -> fromInt(Year.now.getValue))

  val sampleSpELWithAdditional: Map[String, Any] = Map("first" -> "Nu", "year" -> Year.now.getValue)

}
