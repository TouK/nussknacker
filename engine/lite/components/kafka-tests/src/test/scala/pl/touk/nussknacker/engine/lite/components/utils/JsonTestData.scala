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

  val schemaInteger: Schema = JsonSchemaBuilder.parseSchema("""{"type": "integer"}""".stripMargin)

  val schemaString: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string"}""".stripMargin)

  val schemaNull: Schema = JsonSchemaBuilder.parseSchema("""{"type": "null"}""".stripMargin)

  val schemaIntegerRange: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
       |  "type": "integer",
       |  "minimum": ${Integer.MIN_VALUE},
       |  "maximum": ${Integer.MAX_VALUE}
       |}""".stripMargin)

  val nameAndLastNameSchema: Schema = nameAndLastNameSchema(true)

  def nameAndLastNameSchema(additionalProperties: Any): Schema = JsonSchemaBuilder.parseSchema(
    s"""{
       |  "type": "object",
       |  "properties": {
       |    "first": {
       |      "type": "string"
       |    },
       |    "last": {
       |      "type": "string"
       |    }
       |  },
       |  "additionalProperties": ${additionalProperties.toString}
       |}""".stripMargin)

  val schemaObjObjFirstLastNameRequired: Schema = JsonSchemaBuilder.parseSchema(
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

  val schemaMapAny: Schema = JsonSchemaBuilder.parseSchema(
    """{"type": "object", "additionalProperties": true}""".stripMargin
  )

  val schemaObjMapAny: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
       |  "type": "object",
       |  "properties": {
       |    "field" : $schemaMapAny
       |   }
       |}
       |""".stripMargin
  )

  val schemaMapInteger: Schema = JsonSchemaBuilder.parseSchema(
    s"""{"type": "object", "additionalProperties": $schemaInteger}""".stripMargin
  )

  val schemaMapString: Schema = JsonSchemaBuilder.parseSchema(
    s"""{"type": "object", "additionalProperties": $schemaString}""".stripMargin
  )

  val schemaMapStringOrInt: Schema = JsonSchemaBuilder.parseSchema(
    s"""{"type": "object", "additionalProperties": { "type": ["string", "integer" ]}}""".stripMargin
  )

  val schemaMapObjPerson: Schema = JsonSchemaBuilder.parseSchema(
    s"""{"type": "object", "additionalProperties": $personSchema}""".stripMargin
  )

  val schemaListIntegers: Schema = JsonSchemaBuilder.parseSchema(
    s"""
      |{
      |  "type": "array",
      |  "items": $schemaInteger
      |}
      |""".stripMargin)

  val schemaObjMapInteger: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
      |  "type": "object",
      |  "properties": {
      |    "field": $schemaMapInteger
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin
  )

  val schemaObjInteger: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
      |  "type": "object",
      |  "properties": {
      |    "field": $schemaInteger
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjString: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
      |  "type": "object",
      |  "properties": {
      |    "field": $schemaString
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjNull: Schema = JsonSchemaBuilder.parseSchema(
    s"""{
      |  "type": "object",
      |  "properties": {
      |    "field": $schemaNull
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field": { "type": ["null", "string"] }
      |   },
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val schemaObjRequiredUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field": { "type": ["null", "string"] }
      |   },
      |   "required": ["field"],
      |   "additionalProperties": false
      |}
      |""".stripMargin)

  val sampleInteger: Json = fromInt(1)

  val sampleObjFirstLastName: Json = obj("first" -> fromString("Nu"), "last" -> fromString("TouK"))

  val sampleObPerson: Json = obj("first" -> fromString("Nu"), "last" -> fromString("TouK"), "age" -> fromInt(10))

  val sampleSpELFirstLastName: Map[String, Any] = Map("first" -> "Nu", "last" -> "TouK")

  val sampleMapAny: Json = obj("first" -> fromString("Nu"), "year" -> fromInt(Year.now.getValue))

  val sampleMapInteger: Json = obj("year" -> fromInt(Year.now.getValue))

  val sampleMapString: Json = obj("foo" -> fromString("bar"))

  val sampleMapSpELAny: Map[String, Any] = Map("first" -> "Nu", "year" -> Year.now.getValue)

  val sampleMapSpELInteger: Map[String, Integer] = Map("year" -> Year.now.getValue)

}
