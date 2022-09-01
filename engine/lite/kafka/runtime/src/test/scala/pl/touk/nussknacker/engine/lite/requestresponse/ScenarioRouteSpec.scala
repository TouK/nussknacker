package pl.touk.nussknacker.engine.lite.requestresponse

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{InputSchemaProperty, OutputSchemaProperty}
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.Future

class ScenarioRouteSpec extends AnyFunSuite with ScalatestRouteTest with Matchers {

  import spel.Implicits._

  private val inputSchema = """{"type" : "object", "properties": {"city": {"type": "string", "default": "Warsaw"}}}"""
  private val outputSchema = """{"type" : "object", "properties": {"place": {"type": "string"}}}"""
  private val scenario = ScenarioBuilder
    .requestResponse("test")
    .additionalFields(description = Some("description"), properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
    .source("start", "request")
    .emptySink("end", "response", SinkRawEditorParamName -> "false", "place" -> "#input.city")

  private val modelData = LocalModelData(ConfigFactory.load(), new EmptyProcessConfigCreator)

  private val scenarioName: ProcessName = ProcessName(scenario.metaData.id)

  private val interpreter = RequestResponseInterpreter[Future](
    scenario, ProcessVersion.empty.copy(processName = scenarioName), LiteEngineRuntimeContextPreparer.noOp, modelData,
    Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime
  ).valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  private val routes = new ScenarioRoute(new RequestResponseAkkaHttpHandler(interpreter),
    OpenApiDefinitionConfig(Some(OApiServer("https://nussknacker.io", "request response test"))), scenarioName, "/").combinedRoute

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    interpreter.open()
  }

  override protected def afterAll(): Unit = {
    interpreter.close()
    super.afterAll()
  }

  private val expectedOApiDef =
    """{
      |  "openapi" : "3.1.0",
      |  "info" : {
      |    "title" : "test",
      |    "description" : "description",
      |    "version" : "1"
      |  },
      |  "servers" : [
      |    {
      |      "url" : "https://nussknacker.io",
      |      "description" : "request response test"
      |    }
      |  ],
      |  "paths" : {
      |    "/" : {
      |      "post" : {
      |        "description" : "**scenario name**: test",
      |        "tags" : [
      |          "Nussknacker"
      |        ],
      |        "requestBody" : {
      |          "required" : true,
      |          "content" : {
      |            "application/json" : {
      |              "schema" : {
      |                "type" : "object",
      |                "nullable" : false,
      |                "properties" : {
      |                  "city" : {
      |                    "type" : "string",
      |                    "nullable" : false,
      |                    "default" : "Warsaw"
      |                  }
      |                }
      |              }
      |            }
      |          }
      |        },
      |        "produces" : [
      |          "application/json"
      |        ],
      |        "consumes" : [
      |          "application/json"
      |        ],
      |        "summary" : "test",
      |        "responses" : {
      |          "200" : {
      |            "content" : {
      |              "application/json" : {
      |                "schema" : {
      |                  "type" : "object",
      |                  "properties" : {
      |                    "place" : {
      |                      "type" : "string"
      |                    }
      |                  }
      |                }
      |              }
      |            }
      |          }
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin

  test("get scenario openapi definition") {
    Get("/definition") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe expectedOApiDef
    }
  }

  test("handle post") {
    val msg = """{"city":"London"}"""
    Post("/", HttpEntity(ContentTypes.`application/json`, msg)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe s"""{"place":"London"}"""
    }
  }

}

