package pl.touk.nussknacker.engine.requestresponse

import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

class ScenarioRouteNoAuthSpec extends BaseScenarioRouteSpec with Matchers {

  private val definitionConfig: OpenApiDefinitionConfig = OpenApiDefinitionConfig(
    servers = List(OApiServer("https://nussknacker.io", Some("request response test"))),
  )

  override protected val config: RequestResponseConfig = RequestResponseConfig(definitionConfig, security = None)

  private val expectedOApiDef = parse(s"""
    |{
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

  test("get scenario openapi definition for unconfigured auth") {
    Get("/definition") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      parse(responseAs[String]) shouldBe expectedOApiDef
    }
  }

  test("handle post with open route") {
    val msg = """{"city":"London"}"""
    Post("/", HttpEntity(ContentTypes.`application/json`, msg))
      .addCredentials(BasicHttpCredentials("publisher", "password")) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe s"""{"place":"London"}"""
    }
  }

}
