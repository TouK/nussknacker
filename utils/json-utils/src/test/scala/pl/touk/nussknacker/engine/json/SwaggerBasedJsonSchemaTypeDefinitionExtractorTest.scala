package pl.touk.nussknacker.engine.json

import io.circe.Json
import io.circe.Json.fromString
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.json.swagger.{SwaggerDateTime, SwaggerObject}
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToObject

class SwaggerBasedJsonSchemaTypeDefinitionExtractorTest extends AnyFunSuite {

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

  //todo nested refs not work in openapi 3.0.x
  ignore("should support refs") {
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
        |				"b" : {"$ref" : "#/definitions/size"}
        |			}
        |		}
        |	}
        |}
        |""".stripMargin))

    val result = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

    val results = List(
      "rectangle" -> TypedObjectTypingResult.apply(
        List(
          "a" -> Typed.apply[java.math.BigDecimal],
          "b" -> Typed.apply[java.math.BigDecimal]
        )
      ),
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
    val swaggerObject = new SwaggerObject(elementType = Map("time" -> SwaggerDateTime), Set())
    val jsonToObjectExtracted = JsonToObject.apply(jsonObject, swaggerObject)

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

}
