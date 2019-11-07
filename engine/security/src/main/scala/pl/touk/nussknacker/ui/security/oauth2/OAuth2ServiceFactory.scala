package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.LoggedUser

import scala.concurrent.Future

trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def authorize(token: String): Future[LoggedUser]
}

trait OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service
}

case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])
