package pl.touk.nussknacker.ui.security.oauth2

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def authorize(token: String): Future[LoggedUser]
}

trait OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service
}

case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])
