package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client3.SttpBackend

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

trait OAuth2AuthorizationData {
  val accessToken: String
  val expirationPeriod: Option[FiniteDuration]
  val tokenType: String
  val refreshToken: Option[String]
}

abstract class OAuth2Service[+UserInfoData, +AuthorizationData <: OAuth2AuthorizationData](
    implicit ec: ExecutionContext
) {

  /*
  According to the OAuth2 specification, the redirect URI previously passed to the authorization endpoint is required
  along with an authorization code to obtain an access token. At this step, the URI is used solely for verification.
   String comparison is performed by the authorization server, hence the type.
   */
  def obtainAuthorizationAndAuthenticateUser(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, UserInfoData)]

  def checkAuthorizationAndAuthenticateUser(accessToken: String): Future[(UserInfoData, Option[Instant])] = {
    for {
      accessTokenData <- introspectAccessToken(accessToken)
      userInfo        <- authenticateUser(accessToken, accessTokenData)
    } yield (userInfo, accessTokenData.expirationTime)
  }

  private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData]

  private[oauth2] def authenticateUser(
      accessToken: String,
      accessTokenData: IntrospectedAccessTokenData
  ): Future[UserInfoData]

}

final case class IntrospectedAccessTokenData(
    subject: Option[String],
    expirationTime: Option[Instant],
    roles: Set[String]
)

object IntrospectedAccessTokenData {
  val empty: IntrospectedAccessTokenData = IntrospectedAccessTokenData(None, None, Set.empty)
}

trait OAuth2ServiceFactory {

  def create(configuration: OAuth2Configuration)(
      implicit ec: ExecutionContext,
      sttpBackend: SttpBackend[Future, Any]
  ): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] =
    throw new NotImplementedError("Trying to use the new version of the interface, which is not implemented yet")

}
