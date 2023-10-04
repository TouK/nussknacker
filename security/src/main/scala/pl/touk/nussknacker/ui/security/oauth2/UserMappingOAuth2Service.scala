package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class UserMappingOAuth2Service[UserInfoData: Decoder, AuthorizationData <: OAuth2AuthorizationData: Decoder](
    delegate: OAuth2Service[UserInfoData, AuthorizationData],
    authenticatedUserFrom: AuthenticatedUserParameters[UserInfoData] => Future[AuthenticatedUser]
)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Any])
    extends OAuth2Service[AuthenticatedUser, AuthorizationData] {

  def obtainAuthorizationAndAuthenticateUser(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, AuthenticatedUser)] = {
    for {
      (authorization, userInfo) <- delegate.obtainAuthorizationAndAuthenticateUser(authorizationCode, redirectUri)
      authenticatedUser <- authenticatedUserFrom(
        new AuthenticatedUserParameters(IntrospectedAccessTokenData.empty, () => Future.successful(userInfo))
      )
    } yield (authorization, authenticatedUser)
  }

  override private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] =
    delegate.introspectAccessToken(accessToken)

  override private[oauth2] def authenticateUser(
      accessToken: String,
      accessTokenData: IntrospectedAccessTokenData
  ): Future[AuthenticatedUser] = {
    authenticatedUserFrom(
      new AuthenticatedUserParameters(accessTokenData, () => delegate.authenticateUser(accessToken, accessTokenData))
    )
  }

}

final class AuthenticatedUserParameters[UserInfoData](
    val accessTokenData: IntrospectedAccessTokenData,
    val getUserInfo: () => Future[UserInfoData]
)
