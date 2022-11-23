package pl.touk.nussknacker.engine.json

import io.circe.Json
import io.circe.Json.fromString
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.json.swagger.{AdditionalPropertiesDisabled, AdditionalPropertiesSwaggerTyped, SwaggerDateTime, SwaggerLong, SwaggerObject, SwaggerString}
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct

class SwaggerBasedJsonSchemaTypeDefinitionExtractorTest extends AnyFunSuite  with TableDrivenPropertyChecks {

  test("should convert schema additionalProperties to swagger type") {
    val baseSwaggerTyped: SwaggerObject = SwaggerObject(Map("field" -> SwaggerString))
    val testData = Table(
      ("schemaStr", "expected"),
      (
        """{"type": "object", "properties": {"field": {"type": "string"}}}""",
        baseSwaggerTyped
      ),
      (
        """{"type": "object", "properties": {"field": {"type": "string"}}, "additionalProperties": true}""",
        baseSwaggerTyped
      ),
      (
        """{"type": "object", "properties": {"field": {"type": "string"}}, "additionalProperties": {"type": "integer"}}""",
        baseSwaggerTyped.copy(additionalProperties = AdditionalPropertiesSwaggerTyped(SwaggerLong))
      ),
      (
        """{"type": "object", "properties": {"field": {"type": "string"}}, "additionalProperties": false}""",
        baseSwaggerTyped.copy(additionalProperties = AdditionalPropertiesDisabled)
      ),
    )

    forAll(testData) { (schemaStr: String, expected: SwaggerObject) =>
      val schema = JsonSchemaBuilder.parseSchema(schemaStr)
      val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema)
      result shouldBe expected
    }

  }

  test("should extract object with simple fields") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
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
        |      "description": "Age in years which must be equal to or greater than zero.",
        |      "type": "integer",
        |      "minimum": 0
        |    },
        |    "profession": {
        |      "type": "array",
        |      "items": {
        |        "type": "string"
        |      }
        |    },
        |  }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "last" -> Typed.apply[String],
      "first" -> Typed.apply[String],
      "age" -> Typed.apply[Long],
      "profession" -> Typed.genericTypeClass(classOf[java.util.List[String]], List(Typed[String])),
    )
    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support refs") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |	"type" : "object",
        |	"properties" : {
        |		"rectangle" : {"$ref" : "#/definitions/Rectangle" }
        |	},
        |	"definitions" : {
        |		"size" : {
        |			"type" : "number",
        |			"minimum" : 0
        |		},
        |		"Rectangle" : {
        |			"type" : "object",
        |			"properties" : {
        |				"a" : {"$ref" : "#/definitions/size"},
        |				"b" : {"$ref" : "#/definitions/size"},
        |				"c" : {"$ref" : "#/$defs/name"}
        |			}
        |		},
        |   "foo": "bar"
        |	},
        |	"$defs" : {
        |   "name": { "type": "string" }
        | },
        | "foo": "bar"
        |}
        |""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "rectangle" -> TypedObjectTypingResult.apply(
        List(
          "a" -> Typed.apply[java.math.BigDecimal],
          "b" -> Typed.apply[java.math.BigDecimal],
          "c" -> Typed.apply[String]
        )
      ),
    )
    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support refs using /schemas") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |  "type": "object",
        |  "title": "test_pfl",
        |  "description": "sample schema",
        |  "id": "/schema/sampleschema1",
        |  "$defs": {
        |    "nestedObject": {
        |      "id": "/schemas/nestedObject",
        |      "$schema": "http://json-schema.org/draft-04/schema#",
        |      "type": "object",
        |      "properties": {
        |        "numFieldObj": {
        |          "type": "number"
        |        },
        |        "strFieldObj": {
        |          "type": "string"
        |        }
        |      }
        |    }
        |  },
        |  "$schema": "http://json-schema.org/draft-04/schema#",
        |  "required": [
        |    "intField"
        |  ],
        |  "properties": {
        |    "numField": {
        |      "type": "number"
        |    },
        |    "strField": {
        |      "type": "string",
        |      "default": "defaultValue"
        |    },
        |    "intArrayField": {
        |      "type": "array",
        |      "items": {
        |        "type": "integer"
        |      }
        |    },
        |    "intField": {
        |      "type": "integer"
        |    },
        |    "nestedObject": {
        |      "$ref": "/schemas/nestedObject"
        |    },
        |    "boolField": {
        |      "type": "boolean"
        |    }
        |  }
        |}
        |""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "boolField" -> Typed.apply[Boolean],
      "intArrayField" -> Typed.genericTypeClass(classOf[java.util.List[Long]], List(Typed[Long])),
      "intField" -> Typed.apply[Long],
      "nestedObject" -> TypedObjectTypingResult(List(
        "numFieldObj" -> Typed.apply[java.math.BigDecimal],
        "strFieldObj" -> Typed.apply[String]
      )),
      "strField" -> Typed.apply[String],
      "numField" -> Typed.apply[java.math.BigDecimal]
    )
    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support enums") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "profession": {
        |      "type": "array",
        |      "items": {
        |        "type": "string",
        |        "enum": ["one", "two", "three"]
        |      }
        |    },
        |  }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "profession" -> Typed.genericTypeClass(classOf[java.util.List[String]], List(Typed[String])),
    )
    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support nested schema") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |         "type":"string"
        |      },
        |      "nested":{
        |         "type":"array",
        |         "items":{
        |            "type":"object",
        |            "properties":{
        |               "id":{
        |                  "type":"string"
        |               },
        |               "nested2": {
        |                   "type":"object",
        |                   "properties":{
        |                      "name":{
        |                         "type":"string"
        |                       },
        |                   }
        |               }
        |            },
        |            "required":[
        |               "id",
        |               "nested2"
        |            ]
        |         }
        |      }
        |   },
        |   "required":[
        |      "id",
        |      "nested"
        |   ]
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "id" -> Typed.apply[String],
      "nested" -> Typed.genericTypeClass(classOf[java.util.List[_]],
        List(TypedObjectTypingResult.apply(List(
          "id" -> Typed.apply[String],
          "nested2" -> TypedObjectTypingResult.apply(List("name" -> Typed.apply[String]))
        )))))
    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("typed schema should produce same typingResult as typed swagger for SwaggerDateTime") {
    val jsonSchema = new JSONObject(
      """{
        |   "properties":{
        |      "time":{
        |         "type":"string",
        |         "format":"date-time"
        |      }
        |   }
        |}""".stripMargin)

    val schema = SchemaLoader.load(jsonSchema)
    val swaggerTypeExtracted = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val jsonObject = Json.obj("time" -> fromString("2022-07-11T18:12:27+02:00"))
    val swaggerObject = new SwaggerObject(elementType = Map("time" -> SwaggerDateTime), AdditionalPropertiesDisabled)
    val jsonToObjectExtracted = JsonToNuStruct(jsonObject, swaggerObject)

    swaggerTypeExtracted.asInstanceOf[TypedObjectTypingResult].fields("time") shouldBe
      Typed.fromInstance(jsonToObjectExtracted.asInstanceOf[TypedMap].get("time"))

  }

  test("should support schema without type") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "properties":{
        |      "id":{
        |         "type":"string"
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed.apply[String])

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support union - constructed with 'type' array") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |         "type":["string", "integer"]
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed(Set(Typed.apply[String], Typed.apply[Long])))

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support union - constructed with 'oneOf'") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "oneOf": [
        |          { "type": "string" },
        |          { "type": "integer" }
        |        ]
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed(Set(Typed.apply[String], Typed.apply[Long])))

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support union - constructed with 'anyOf'") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "anyOf": [
        |          { "type": "string" },
        |          { "type": "integer" }
        |        ]
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed(Set(Typed.apply[String], Typed.apply[Long])))

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support support multiple schemas but of the same type") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "oneOf": [
        |          { "type": "integer", "multipleOf": 5 },
        |          { "type": "integer", "multipleOf": 3 }
        |        ]
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed.apply[Long])

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support support multiple schemas but of the same type - factored version") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "type": "integer",
        |        "oneOf": [
        |          { "multipleOf": 5 },
        |          { "multipleOf": 3 }
        |        ]
        |      }
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed.apply[Long])

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should support generic map") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "additionalProperties": true
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown))
  }

  test("should support map with typed values") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "additionalProperties": {
        |     "type":"integer"
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Typed[Long]))
  }

  test("should ignore additionalProperties when at least one property is defined explicitly") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "type": "string",
        |      }
        |   },
        |   "additionalProperties": {
        |     "type":"integer"
        |   }
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List("id" -> Typed.apply[String])

    result shouldBe TypedObjectTypingResult.apply(results)
  }

  test("should type empty object when additionalProperties is false and no explicitly defined properties") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |   "type":"object",
        |   "additionalProperties": false
        |}""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe TypedObjectTypingResult.apply(List.empty)
  }
}
