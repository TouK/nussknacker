package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

trait OAuth2AuthorizationData {
  val accessToken: String
  val expirationPeriod: Option[FiniteDuration]
  val tokenType: String
  val refreshToken: Option[String]
}

trait OAuth2Service[+UserInfoData, +AuthorizationData <: OAuth2AuthorizationData] {
  /*
  According to the OAuth2 specification, the redirect URI previously passed to the authorization endpoint is required
  along with an authorization code to obtain an access token. At this step, the URI is used solely for verification.
   String comparison is performed by the authorization server, hence the type.
   */
  def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(AuthorizationData, UserInfoData)]
  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])]
}


trait OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] =
    throw new NotImplementedError("Trying to use the new version of the interface, which is not implemented yet")
}