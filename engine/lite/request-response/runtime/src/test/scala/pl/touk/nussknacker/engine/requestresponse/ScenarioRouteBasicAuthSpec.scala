package pl.touk.nussknacker.engine.requestresponse

import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import org.apache.pekko.http.scaladsl.server
import org.apache.pekko.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

class ScenarioRouteBasicAuthSpec extends BaseScenarioRouteSpec with Matchers {

  protected val password = "password"
  protected val securityConfig =
    Some(RequestResponseSecurityConfig(basicAuth = Some(BasicAuthConfig(user = "publisher", password = password))))

  private val definitionConfig: OpenApiDefinitionConfig = OpenApiDefinitionConfig(
    List(OApiServer("https://nussknacker.io", Some("request response test")))
  )

  override protected val config: RequestResponseConfig = RequestResponseConfig(definitionConfig, securityConfig)

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
      |        },
      |        "security": [
      |          { "basicAuthScheme": [] }
      |        ]
      |      }
      |    }
      |  },
      |  "components" : {
      |    "securitySchemes" : {
      |      "basicAuthScheme" : {
      |        "type" : "http",
      |        "scheme" : "basic"
      |      }
      |    }
      |  }
      |}""".stripMargin)

  test("get scenario openapi definition for basic auth") {
    Get("/definition") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      parse(responseAs[String]) shouldBe expectedOApiDef
    }
  }

  test("handle post with valid credentials on secured route") {
    val msg = """{"city":"London"}"""
    Post("/", HttpEntity(ContentTypes.`application/json`, msg))
      .addCredentials(BasicHttpCredentials("publisher", "password")) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe s"""{"place":"London"}"""
    }
  }

  test("reject post with bad password on secured route") {
    val msg              = """{"city":"London"}"""
    val wrongCredentials = BasicHttpCredentials("publisher", "WRONG_PASSWORD")
    Post("/", HttpEntity(ContentTypes.`application/json`, msg)) ~> addCredentials(
      wrongCredentials
    ) ~> routes ~> check {
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
    ) ~> routes ~> check {
      rejection shouldBe server.AuthenticationFailedRejection(
        CredentialsRejected,
        HttpChallenge("Basic", "request-response", Map("charset" -> "UTF-8"))
      )
    }
  }

}
