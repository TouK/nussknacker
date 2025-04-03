package pl.touk.nussknacker.ui.customhttpservice

import com.typesafe.config.Config
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, WithTestHttpClient}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{ConfigWithScalaVersion, WithDesignerConfig}
import sttp.client3.{asStringAlways, quickRequest, UriContext}
import sttp.model.StatusCode

class CustomHttpServiceTest
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with WithTestHttpClient
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage {

  override def designerRawConfig: Config = ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine

  "For the Pekko based CustomHttpService" - {
    "when request send with authentication data" - {
      "should return 200 OK response" in {
        val response = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
            .auth
            .basic("admin", "admin")
        )
        response.code shouldEqual StatusCode.Ok
        response.body shouldEqual "testResponse"
      }

      "should return 200 OK response send to second CustomHttpService" in {
        val response = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/custom/secondTestProvider/testPathPart")
            .auth
            .basic("admin", "admin")
        )
        response.code shouldEqual StatusCode.Ok
        response.body shouldEqual "testResponse"
      }
    }

    "when request send without authentication data" - {
      "should return 401 Unauthorized response" in {
        val response = httpClient.send(
          quickRequest.get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
        )
        response.code shouldEqual StatusCode.Unauthorized
        response.body shouldEqual "The resource requires authentication, which was not supplied with the request"
      }
    }
  }

  "For the Tapir based CustomHttpService" - {
    "when request send to secured endpoint with authentication data" - {
      "should return 200 OK response" in {
        val response = httpClient.send(
          quickRequest
            .post(uri"$nuDesignerHttpAddress/api/custom/tapirTestProvider/secured")
            .body("""{"message":"Hello"}""")
            .auth
            .basic("admin", "admin")
        )
        response.code shouldEqual StatusCode.Ok
        response.body shouldEqual """{"message":"You send message: 'Hello''","username":"admin"}"""
      }

      "should return business error" in {
        val response = httpClient.send(
          quickRequest
            .post(uri"$nuDesignerHttpAddress/api/custom/tapirTestProvider/secured")
            .body("""{"message":"Hello","returnBusinessError":true}""")
            .auth
            .basic("admin", "admin")
        )
        response.code shouldEqual StatusCode.UnprocessableEntity
        response.body shouldEqual "Sample error"
      }

      "should return internal error" in {
        val response = httpClient.send(
          quickRequest
            .post(uri"$nuDesignerHttpAddress/api/custom/tapirTestProvider/secured")
            .body("""{"message":"Hello","returnInternalError":true}""")
            .auth
            .basic("admin", "admin")
        )
        response.code shouldEqual StatusCode.InternalServerError
        response.body shouldEqual "Internal error"
      }
    }

    "when request send to secured endpoint without authentication data" - {
      "should return 401 Unauthorized response" in {
        val response = httpClient.send(
          quickRequest
            .post(uri"$nuDesignerHttpAddress/api/custom/tapirTestProvider/secured")
            .body("""{"message":"Hello"}""")
        )
        response.code shouldEqual StatusCode.Unauthorized
        response.body shouldEqual "Invalid value (missing)"
      }
    }

    "when request send to public endpoint without authentication data" - {
      "should return 200 OK response" in {
        val response = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/custom/tapirTestProvider/public")
        )
        response.code shouldEqual StatusCode.Ok
        response.body shouldEqual "Hello from public endpoint!"
      }
    }

    "when request send to API docs" - {
      "should return also docs of custom http service" in {
        val response = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/docs/nu-designer-openapi.yaml")
            .response(asStringAlways.map(io.circe.yaml.parser.parse))
        )
        response.code shouldEqual StatusCode.Ok
        val customServicesPaths =
          response.body.rightValue.hcursor.downField("paths").keys.value.filter(_.contains("/api/custom"))
        customServicesPaths should contain allOf (
          "/api/custom/tapirTestProvider/public",
          "/api/custom/tapirTestProvider/secured",
        )
      }
    }
  }

}
