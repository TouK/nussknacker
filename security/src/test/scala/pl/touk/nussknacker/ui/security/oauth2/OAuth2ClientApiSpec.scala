package pl.touk.nussknacker.ui.security.oauth2

import org.scalatest.{BeforeAndAfter, Suite}
import org.scalatest.Inside.inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import pl.touk.nussknacker.ui.security.oidc.DefaultOidcAuthorizationData
import sttp.client3.StringBody
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, HeaderNames, MediaType, Uri}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2ClientApiSpec extends AnyFlatSpec with Matchers with BeforeAndAfter with PatientScalaFutures with Suite {
  import io.circe.syntax._

  val config = ExampleOAuth2ServiceFactory.testConfig

  val body = DefaultOidcAuthorizationData(accessToken = "9IDpWSEYetSNRX41", tokenType = "Bearer", refreshToken = None)

  implicit val testingBackend: RecordingSttpBackend[Future, Any] = new RecordingSttpBackend[Future, Any](
    SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(body.asJson.toString())
  )

  before {
    testingBackend.clear()
  }

  it should ("send access token request in urlencoded") in {
    val client = new OAuth2ClientApi[GitHubProfileResponse, DefaultOidcAuthorizationData](
      config.copy(accessTokenRequestContentType = MediaType.ApplicationXWwwFormUrlencoded.toString())
    )

    client.accessTokenRequest("6V1reBXblpmfjRJP", "http://ignored").futureValue

    val request = testingBackend.allInteractions.head._1
    inside(request.body) { case StringBody(s, _, _) =>
      s should include(s"client_id=${config.clientId}")
    }
    request.headers should contain
    (Header(HeaderNames.ContentType, MediaType.ApplicationXWwwFormUrlencoded.toString()))
  }

  it should ("send access token request as json") in {
    val client = new OAuth2ClientApi[GitHubProfileResponse, DefaultOidcAuthorizationData](
      config.copy(accessTokenRequestContentType = MediaType.ApplicationJson.toString())
    )

    client.accessTokenRequest("6V1reBXblpmfjRJP", "http://ignored").futureValue

    val request = testingBackend.allInteractions.head._1
    inside(request.body) { case StringBody(s, _, _) =>
      s should include(s""""client_id":"${config.clientId}"""")
    }
    request.headers should contain
    (Header(HeaderNames.ContentType, MediaType.ApplicationJson.toString))
  }

}
