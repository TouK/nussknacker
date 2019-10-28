package pl.touk.nussknacker.ui.security

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.OAuth2TestServiceFactory.{TestAccessTokenResponse, TestProfileResponse}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2ClientApi, OAuth2Configuration, OAuth2Service}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestOAuth2Service(clientApi: OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map{ resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = Option.empty
      )
    }
  }

  override def profile(token: String): Future[OAuth2Profile] = {
    clientApi.profileRequest(token).map{ prf =>
      OAuth2Profile(
        id = prf.uid,
        email = prf.email,
        roles = List.empty
      )
    }
  }
}

object OAuth2TestServiceFactory {
  def apply(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service
    = new TestOAuth2Service(getTestClient(configuration), configuration)

  def getTestClient(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse]
  = new OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration)

  @JsonCodec case class TestAccessTokenResponse(access_token: String, token_type: String)
  @JsonCodec case class TestProfileResponse(email: String, uid: String)
}