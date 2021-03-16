package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

// Uncomment when we drop Scala 2.11
//@deprecatedInheritance("Implement OAuth2Service probably by extending BaseOAuthService", "0.4.0")
trait OAuth2OldService extends OAuth2Service[LoggedUser, OAuth2AuthenticateData] {
  implicit val ec: ExecutionContext

  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def authorize(token: String): Future[LoggedUser]

  final override def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(OAuth2AuthenticateData, Option[LoggedUser])] =
    authenticate(authorizationCode).map((_, None))
  final override def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(LoggedUser, Option[Deadline])] =
    authorize(accessToken).map((_, None))
}

case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String]) extends OAuth2AuthorizationData {
  val accessToken: String = access_token
  val expirationPeriod: Option[FiniteDuration] = None
  val tokenType: String = token_type
  val refreshToken: Option[String] = refresh_token
}