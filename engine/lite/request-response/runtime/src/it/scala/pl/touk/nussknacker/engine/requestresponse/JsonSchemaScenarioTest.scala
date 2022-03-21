package pl.touk.nussknacker.engine.requestresponse

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreterSpec.prepareInterpreter
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{InputSchemaProperty, OutputSchemaProperty}
import pl.touk.nussknacker.engine.spel.Implicits._

class JsonSchemaScenarioTest extends FunSuite with Matchers {

  test("render schema for process") {
    val inputSchema = "{\"properties\": {\"city\": {\"type\": \"string\", \"default\": \"Warsaw\"}}}"
    val outputSchema = "{\"properties\": {\"place\": {\"type\": \"string\"}}}"
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .additionalFields(properties = Map("paramName" -> "paramValue", InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
      .source("start", "jsonSchemaRequest")
      .emptySink("endNodeIID", "jsonSchemaResponse", "Value" -> "#input")

    val interpreter = prepareInterpreter(process = process, new JsonSchemaRequestResponseConfigCreator)
    val openApiOpt = interpreter.generateOpenApiDefinition()
    val expectedOpenApi =
      """{
        |  "post" : {
        |    "description" : "**scenario name**: proc1",
        |    "tags" : [
        |      "Nussknacker"
        |    ],
        |    "requestBody" : {
        |      "required" : true,
        |      "content" : {
        |        "application/json" : {
        |          "schema" : {
        |            "properties" : {
        |              "city" : {
        |                "type" : "string",
        |                "default" : "Warsaw"
        |              }
        |            }
        |          }
        |        }
        |      }
        |    },
        |    "produces" : [
        |      "application/json"
        |    ],
        |    "consumes" : [
        |      "application/json"
        |    ],
        |    "summary" : "proc1",
        |    "responses" : {
        |      "200" : {
        |        "content" : {
        |          "application/json" : {
        |            "schema" : {
        |              "properties" : {
        |                "place" : {
        |                  "type" : "string"
        |                }
        |              }
        |            }
        |          }
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin

    openApiOpt shouldBe defined
    openApiOpt.get.spaces2 shouldBe expectedOpenApi
  }

}
