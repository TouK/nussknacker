package pl.touk.nussknacker.engine.requestresponse

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import io.circe.parser._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ComponentUseContextProvider
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{
  InputSchemaProperty,
  OutputSchemaProperty
}
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.Future

class ScenarioRouteSpec extends AnyFunSuite with ScalatestRouteTest with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val inputSchema  = """{"type" : "object", "properties": {"city": {"type": "string", "default": "Warsaw"}}}"""
  private val outputSchema = """{"type" : "object", "properties": {"place": {"type": "string"}}}"""

  private val scenario = ScenarioBuilder
    .requestResponse("test")
    .additionalFields(
      description = Some("description"),
      properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema)
    )
    .source("start", "request")
    .emptySink("end", "response", SinkRawEditorParamName.value -> "false".spel, "place" -> "#input.city".spel)

  private val modelData =
    LocalModelData(ConfigFactory.load(), RequestResponseComponentProvider.Components)

  private val scenarioName: ProcessName = scenario.name

  private val interpreter = RequestResponseInterpreter[Future](
    scenario,
    ProcessVersion.empty.copy(processName = scenarioName),
    LiteEngineRuntimeContextPreparer.noOp,
    modelData,
    Nil,
    ProductionServiceInvocationCollector,
    ComponentUseContextProvider.LiveRuntime,
  ).valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  private val definitionConfig: OpenApiDefinitionConfig = OpenApiDefinitionConfig(
    List(OApiServer("https://nussknacker.io", Some("request response test")))
  )

  private val requestResponseConfig: RequestResponseConfig = RequestResponseConfig(definitionConfig)
  private val openRoutes =
    new ScenarioRoute(new RequestResponseHttpHandler(interpreter), requestResponseConfig, scenarioName).combinedRoute
  private val password = "password"
  private val securityConfig: RequestResponseSecurityConfig =
    RequestResponseSecurityConfig(basicAuth = Some(BasicAuthConfig(user = "publisher", password = password)))

  private val securedRoutes = new ScenarioRoute(
    new RequestResponseHttpHandler(interpreter),
    requestResponseConfig.copy(security = Some(securityConfig)),
    scenarioName
  ).combinedRoute

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    interpreter.open()
  }

  override protected def afterAll(): Unit = {
    interpreter.close()
    super.afterAll()
  }

  private val expectedOApiDef = parse(s"""{
       |  "openapi" : "$defaultOpenApiVersion",
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
       |        "operationId" : "test",
      |        "requestBody" : {
       |          "required" : true,
       |          "content" : {
       |            "application/json" : {
       |              "schema" : {
       |                "type" : "object",
       |                "properties" : {
       |                  "city" : {
       |                    "type" : "string",
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
       |}""".stripMargin)

  test("get scenario openapi definition") {
    Get("/definition") ~> openRoutes ~> check {
      status shouldEqual StatusCodes.OK
      parse(responseAs[String]) shouldBe expectedOApiDef
    }
  }

  test("handle post with open route") {
    val msg = """{"city":"London"}"""
    Post("/", HttpEntity(ContentTypes.`application/json`, msg))
      .addCredentials(BasicHttpCredentials("publisher", "password")) ~> openRoutes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe s"""{"place":"London"}"""
    }
  }

  test("handle post with valid credentials on secured route") {
    val msg = """{"city":"London"}"""
    Post("/", HttpEntity(ContentTypes.`application/json`, msg))
      .addCredentials(BasicHttpCredentials("publisher", "password")) ~> securedRoutes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe s"""{"place":"London"}"""
    }
  }

  test("reject post with bad password on secured route") {
    val msg              = """{"city":"London"}"""
    val wrongCredentials = BasicHttpCredentials("publisher", "WRONG_PASSWORD")
    Post("/", HttpEntity(ContentTypes.`application/json`, msg)) ~> addCredentials(
      wrongCredentials
    ) ~> securedRoutes ~> check {
      rejection shouldBe server.AuthenticationFailedRejection(
        CredentialsRejected,
        HttpChallenge("Basic", "request-response", Map("charset" -> "UTF-8"))
      )
    }
  }

  test("reject post with bad user on secured route") {
    val msg              = """{"city":"London"}"""
    val wrongCredentials = BasicHttpCredentials("BAD_USER", "password")
    Post("/", HttpEntity(ContentTypes.`application/json`, msg)) ~> addCredentials(
      wrongCredentials
    ) ~> securedRoutes ~> check {
      rejection shouldBe server.AuthenticationFailedRejection(
        CredentialsRejected,
        HttpChallenge("Basic", "request-response", Map("charset" -> "UTF-8"))
      )
    }
  }

}
