package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import pl.touk.nussknacker.restmodel.security.AuthenticatedUser
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class UserMappingOAuth2Service[UserInfoData: Decoder, AuthorizationData <: OAuth2AuthorizationData: Decoder](
    delegate: OAuth2Service[UserInfoData, AuthorizationData],
    authenticationStrategy: AuthenticationStrategy[UserInfoData]
)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Any])
    extends OAuth2Service[AuthenticatedUser, AuthorizationData] {

  def obtainAuthorizationAndAuthenticateUser(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, AuthenticatedUser)] = {
    for {
      (authorization, userInfo) <- delegate.obtainAuthorizationAndAuthenticateUser(authorizationCode, redirectUri)
      authenticatedUser <- authenticationStrategy.authenticateUser(
        IntrospectedAccessTokenData.empty,
        Future.successful(userInfo)
      )
    } yield (authorization, authenticatedUser)
  }

  override private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] =
    delegate.introspectAccessToken(accessToken)

  override private[oauth2] def authenticateUser(
      accessToken: String,
      accessTokenData: IntrospectedAccessTokenData
  ): Future[AuthenticatedUser] = {
    authenticationStrategy.authenticateUser(accessTokenData, delegate.authenticateUser(accessToken, accessTokenData))
  }

}
