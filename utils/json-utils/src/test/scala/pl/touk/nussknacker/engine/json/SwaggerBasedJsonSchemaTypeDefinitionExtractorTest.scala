package pl.touk.nussknacker.engine.json

import io.circe.Json
import io.circe.Json.fromString
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.json.swagger.decode.FromJsonSchemaBasedDecoder
import pl.touk.nussknacker.engine.json.swagger._

class SwaggerBasedJsonSchemaTypeDefinitionExtractorTest extends AnyFunSuite with TableDrivenPropertyChecks {

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
        baseSwaggerTyped.copy(additionalProperties = AdditionalPropertiesEnabled(SwaggerLong))
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
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map(
      "last"       -> Typed.apply[String],
      "first"      -> Typed.apply[String],
      "age"        -> Typed.apply[Long],
      "profession" -> Typed.genericTypeClass(classOf[java.util.List[String]], List(Typed[String])),
    )
    result shouldBe Typed.record(results)
  }

  test("should support refs") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map(
      "rectangle" -> Typed.record(
        Map(
          "a" -> Typed.apply[java.math.BigDecimal],
          "b" -> Typed.apply[java.math.BigDecimal],
          "c" -> Typed.apply[String]
        )
      ),
    )
    result shouldBe Typed.record(results)
  }

  test("should support refs using /schemas") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map(
      "boolField"     -> Typed.apply[Boolean],
      "intArrayField" -> Typed.genericTypeClass(classOf[java.util.List[Long]], List(Typed[Long])),
      "intField"      -> Typed.apply[Long],
      "nestedObject" -> Typed.record(
        Map(
          "numFieldObj" -> Typed.apply[java.math.BigDecimal],
          "strFieldObj" -> Typed.apply[String]
        )
      ),
      "strField" -> Typed.apply[String],
      "numField" -> Typed.apply[java.math.BigDecimal]
    )
    result shouldBe Typed.record(results)
  }

  test("should support enums of strings") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val enumType = Typed(Typed.fromInstance("one"), Typed.fromInstance("two"), Typed.fromInstance("three"))
    val results = Map(
      "profession" -> Typed.genericTypeClass(classOf[java.util.List[String]], List(enumType)),
    )
    result shouldBe Typed.record(results)

    val onlyEnumSchema = JsonSchemaBuilder.parseSchema("""{ "enum": ["one", "two", "three"] }""".stripMargin)
    SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(onlyEnumSchema).typingResult shouldBe enumType
  }

  test("should support enums of various types") {
    val schema = JsonSchemaBuilder.parseSchema(
      """{"enum": ["one", 2, 3.3, {"four": {"four": 4}, "arr": [4]}, ["five", 5, {"five" : 5}], true, null]}"""
    )
    import scala.jdk.CollectionConverters._

    val expected = Typed(
      Typed.fromInstance("one"),
      Typed.fromInstance(2),
      Typed.fromInstance(3.3),
      Typed.fromInstance(Map("four" -> Map("four" -> 4).asJava, "arr" -> List(4).asJava).asJava),
      Typed.fromInstance(List("five", 5, Map("five" -> 5).asJava).asJava),
      Typed.fromInstance(true),
    )

    SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult shouldBe expected
  }

  test("should support nested schema") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map(
      "id" -> Typed.apply[String],
      "nested" -> Typed.genericTypeClass(
        classOf[java.util.List[_]],
        List(
          Typed.record(
            Map(
              "id"      -> Typed.apply[String],
              "nested2" -> Typed.record(Map("name" -> Typed.apply[String]))
            )
          )
        )
      )
    )
    result shouldBe Typed.record(results)
  }

  test("typed schema should produce same typingResult as typed swagger for SwaggerDateTime") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type": "object",
        |   "properties":{
        |      "time":{
        |         "type":"string",
        |         "format":"date-time"
        |      }
        |   }
        |}""".stripMargin)

    val swaggerTypeExtracted = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val jsonObject    = Json.obj("time" -> fromString("2022-07-11T18:12:27+02:00"))
    val swaggerObject = new SwaggerObject(elementType = Map("time" -> SwaggerDateTime), AdditionalPropertiesDisabled)
    val jsonToObjectExtracted = FromJsonSchemaBasedDecoder.decode(jsonObject, swaggerObject)

    swaggerTypeExtracted.asInstanceOf[TypedObjectTypingResult].fields("time") shouldBe
      Typed.fromInstance(jsonToObjectExtracted.asInstanceOf[TypedMap].get("time"))

  }

  test("should support union - constructed with 'type' array") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |         "type":["string", "integer"]
        |      }
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed(Typed[String], Typed[Long]))

    result shouldBe Typed.record(results)
  }

  test("should support union - constructed with 'oneOf'") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "oneOf": [
        |          { "type": "string" },
        |          { "type": "integer" }
        |        ]
        |      }
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed(Typed[String], Typed[Long]))

    result shouldBe Typed.record(results)
  }

  test("should support union - constructed with 'anyOf'") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "anyOf": [
        |          { "type": "string" },
        |          { "type": "integer" }
        |        ]
        |      }
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed(Typed[String], Typed[Long]))

    result shouldBe Typed.record(results)
  }

  test("should support anyOf/oneOf for object schemas") {
    // TODO: handle case when we have fields common for both versions on "main" level
    def schema(compositionType: String) = JsonSchemaBuilder.parseSchema(s"""{
        |   "type":"object",
        |   "$compositionType": [
        |       { "type": "object", "properties": {"passport": {"type": "string" }} },
        |       { "type": "object", "properties": {"identityCard": {"type": "string" }} },
        |   ]
        |}""".stripMargin)

    Table("type", "anyOf", "oneOf").forEvery { compositionType =>
      val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema(compositionType)).typingResult

      result shouldBe Typed(
        Typed.record(Map("passport" -> Typed[String])),
        Typed.record(Map("identityCard" -> Typed[String])),
      )
    }
  }

  test("should support support multiple schemas but of the same type") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "oneOf": [
        |          { "type": "integer", "multipleOf": 5 },
        |          { "type": "integer", "multipleOf": 3 }
        |        ]
        |      }
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed.apply[Long])

    result shouldBe Typed.record(results)
  }

  test("should support support multiple schemas but of the same type - factored version") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed.apply[Long])

    result shouldBe Typed.record(results)
  }

  test("should support generic map") {
    val schema = JsonSchemaBuilder.parseSchema("""{"type":"object", "additionalProperties": true}""".stripMargin)
    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown))
  }

  test("should support map with typed values") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "additionalProperties": {
        |     "type":"integer"
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Typed[Long]))
  }

  test("should ignore additionalProperties when at least one property is defined explicitly") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "type": "string",
        |      }
        |   },
        |   "additionalProperties": {
        |     "type":"integer"
        |   }
        |}""".stripMargin)

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = Map("id" -> Typed.apply[String])

    result shouldBe Typed.record(results)
  }

  test("should type empty object when additionalProperties is false and no explicitly defined properties") {
    val schema = JsonSchemaBuilder.parseSchema(
      """{"type":"object","additionalProperties": false}""".stripMargin
    )

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.record(Map.empty[String, TypingResult])
  }

  test("should handle Recursive schema parsing") {
    val schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "items": {
        |      "$ref": "#/defs/RecursiveList"
        |    }
        |  },
        |  "defs": {
        |    "RecursiveList": {
        |      "type": "object",
        |      "properties": {
        |        "value": { "type": "string" },
        |        "next": { "$ref": "#/defs/RecursiveList" }
        |      }
        |    }
        |  }
        |}""".stripMargin
    )

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    result shouldBe Typed.record(
      Map("items" -> Typed.record(Map("next" -> Unknown, "value" -> Typed[String])))
    )

  }

  test("should map integer type to the narrowest type possible") {
    def schema(minValue: String, exclusiveMinValue: String, maxValue: String, exclusiveMaxValue: String) =
      JsonSchemaBuilder.parseSchema(s"""{
        |   "type":"object",
        |   "properties":{
        |      "id":{
        |        "type": "integer"
        |        ${Option(minValue).map(min => s""", "minimum":$min""").getOrElse("")}
        |        ${Option(exclusiveMinValue).map(min => s""", "exclusiveMinimum":$min""").getOrElse("")}
        |        ${Option(maxValue).map(max => s""", "maximum":$max""").getOrElse("")}
        |        ${Option(exclusiveMaxValue).map(max => s""", "exclusiveMaximum":$max""").getOrElse("")}
        |       }
        |   }
        |}""".stripMargin)

    val table = Table(
      ("minValue", "exclusiveMinValue", "maxValue", "exclusiveMaxValue", "expectedType"),
      ("10", null, "100", null, classOf[java.lang.Integer]),
      (null, "10", null, "100", classOf[java.lang.Integer]),
      (null, "10", "100", null, classOf[java.lang.Integer]),
      ("10", null, null, "100", classOf[java.lang.Integer]),
      ("10", null, "10", null, classOf[java.lang.Integer]),
      ("100", null, null, null, classOf[java.lang.Long]),
      (null, null, null, "100", classOf[java.lang.Long]),
      (s"${Int.MinValue}", null, s"${Int.MaxValue}", null, classOf[java.lang.Integer]),
      (null, s"${BigInt(Int.MinValue) - 1}", null, s"${BigInt(Int.MaxValue) + 1}", classOf[java.lang.Integer]),
      (s"${BigInt(Int.MinValue) - 1}", null, "0", null, classOf[java.lang.Long]),
      ("0", null, s"${BigInt(Int.MaxValue) + 1}", null, classOf[java.lang.Long]),
      ("0", null, s"${BigInt(Int.MaxValue) + 1}", s"${BigInt(Int.MaxValue) + 1}", classOf[java.lang.Integer]),
      ("0", null, s"${BigInt(Long.MaxValue) + 1},", null, classOf[java.math.BigInteger]),
      ("0", null, s"${BigInt(Long.MaxValue) + 1}", s"${BigInt(Long.MaxValue) + 1}", classOf[java.lang.Long]),
      (null, null, "100", null, classOf[java.lang.Long]),
      (null, null, s"${BigInt(Long.MaxValue) + 1}", null, classOf[java.math.BigInteger]),
      (null, null, null, s"${BigInt(Long.MaxValue) + 1}", classOf[java.lang.Long]),
      (null, null, null, s"${BigInt(Long.MaxValue) + 10}", classOf[java.math.BigInteger]),
      (null, null, s"${BigInt(Int.MaxValue) + 10}", null, classOf[java.lang.Long]),
      (null, null, null, s"${BigInt(Int.MaxValue) + 10}", classOf[java.lang.Long]),
      (s"${BigInt(Int.MinValue) - 1}", null, null, null, classOf[java.lang.Long]),
      (null, s"${BigInt(Int.MinValue) - 1}", null, null, classOf[java.lang.Long]),
      (s"${BigInt(Long.MinValue) - 1}", null, null, null, classOf[java.math.BigInteger]),
      (null, s"${BigInt(Long.MinValue) - 1}", null, null, classOf[java.lang.Long]),
      (null, s"${BigInt(Long.MinValue) - 10}", null, null, classOf[java.math.BigInteger]),
    )

    forAll(table) { (minValue, exclusiveMinValue, maxValue, exclusiveMaxValue, expectedType) =>
      val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor
        .swaggerType(schema(minValue, exclusiveMinValue, maxValue, exclusiveMaxValue))
        .typingResult

      val results = Map("id" -> Typed.typedClass(expectedType))

      result shouldBe Typed.record(results)
    }
  }

}
