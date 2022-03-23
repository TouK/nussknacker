package pl.touk.nussknacker.engine.requestresponse.http

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.JsonRequestResponseSinkFactory.SinkValueParamName
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{InputSchemaProperty, OutputSchemaProperty}
import pl.touk.nussknacker.engine.spel.Implicits._

class RequestResponseHttpOpenApi extends FunSuite with Matchers with RequestResponseInterpreterTest {

  test("render schema for process") {
    val inputSchema = "{\"properties\": {\"city\": {\"type\": \"string\", \"default\": \"Warsaw\"}}}"
    val outputSchema = "{\"properties\": {\"place\": {\"type\": \"string\"}}}"
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .additionalFields(properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
      .source("start", "jsonSchemaRequest")
      .emptySink("end", "jsonSchemaResponse", SinkValueParamName -> """{"place": #input.city}""")

    val interpreter = prepareInterpreter(process = process)
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
        |            "nullable" : false,
        |            "properties" : {
        |              "city" : {
        |                "type" : "string",
        |                "nullable" : false,
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
