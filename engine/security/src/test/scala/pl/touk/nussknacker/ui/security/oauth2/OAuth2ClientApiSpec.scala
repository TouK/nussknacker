package pl.touk.nussknacker.ui.security.oauth2

import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import sttp.client.StringBody
import sttp.client.testing.SttpBackendStub
import sttp.model.{Header, HeaderNames, MediaType, Uri}

class OAuth2ClientApiSpec extends FlatSpec with Matchers with BeforeAndAfter with PatientScalaFutures with Suite  {
  import io.circe.syntax._

  val config = ExampleOAuth2ServiceFactory.testConfig

  val body = DefaultAccessTokenResponse(access_token = "9IDpWSEYetSNRX41", token_type = "Bearer", refresh_token = None)

  implicit val testingBackend = new RecordingSttpBackend(
    SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(body.asJson.toString())
  )

  before {
    testingBackend.clear()
  }

  it should ("send access token request in urlencoded") in {
    val client = new OAuth2ClientApi[GitHubProfileResponse, DefaultAccessTokenResponse](
      config.copy(accessTokenRequestContentType = MediaType.ApplicationXWwwFormUrlencoded.toString())
    )

    client.accessTokenRequest("6V1reBXblpmfjRJP").futureValue

    val request = testingBackend.allInteractions.head._1
    inside(request.body) {
      case StringBody(s, _, _) => s should include (s"client_id=${config.clientId}")
    }
    request.headers should contain
      (Header(HeaderNames.ContentType, MediaType.ApplicationXWwwFormUrlencoded.toString()))
  }

  it should ("send access token request as json") in {
    val client = new OAuth2ClientApi[GitHubProfileResponse, DefaultAccessTokenResponse](
      config.copy(accessTokenRequestContentType = MediaType.ApplicationJson.toString())
    )

    client.accessTokenRequest("6V1reBXblpmfjRJP").futureValue

    val request = testingBackend.allInteractions.head._1
    inside(request.body) {
      case StringBody(s, _, _) => s should include (s""""client_id":"${config.clientId}"""")
    }
    request.headers should contain
      (Header(HeaderNames.ContentType, MediaType.ApplicationJson.toString))
  }

}
