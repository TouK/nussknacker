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
        val response1 = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
            .auth
            .basic("admin", "admin")
        )
        response1.code shouldEqual StatusCode.Ok
        response1.body shouldEqual "testResponse"
      }

      "should return 200 OK response send to second CustomHttpService" in {
        val response1 = httpClient.send(
          quickRequest
            .get(uri"$nuDesignerHttpAddress/api/custom/secondTestProvider/testPathPart")
            .auth
            .basic("admin", "admin")
        )
        response1.code shouldEqual StatusCode.Ok
        response1.body shouldEqual "testResponse"
      }
    }

    "when request send without authentication data" - {
      "should return 401 Unauthorized response" in {
        val response1 = httpClient.send(
          quickRequest.get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
        )
        response1.code shouldEqual StatusCode.Unauthorized
        response1.body shouldEqual "The resource requires authentication, which was not supplied with the request"
      }
    }
  }

  "For the Tapir based CustomHttpService" - {
    "when request send to secured endpoint with authentication data" - {
      "should return 200 OK response" in {
        // TODO
      }
    }

    "when request send to secured endpoint without authentication data" - {
      "should return 401 Unauthorized response" in {
        // TODO
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
        customServicesPaths should contain(
          "/api/custom/tapirTestProvider/public",
          // TODO: more
        )
      }
    }
  }

}
