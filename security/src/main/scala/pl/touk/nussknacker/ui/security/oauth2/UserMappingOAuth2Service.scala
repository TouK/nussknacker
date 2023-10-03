package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class UserMappingOAuth2Service[UserInfoData: Decoder, AuthorizationData <: OAuth2AuthorizationData: Decoder](
    delegate: OAuth2Service[UserInfoData, AuthorizationData],
    loggedUserFunction: LoggedUserFunctionParameters[UserInfoData] => Future[AuthenticatedUser]
)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Any])
    extends OAuth2Service[AuthenticatedUser, AuthorizationData] {

  def obtainAuthorizationAndUserInfo(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, AuthenticatedUser)] = {
    for {
      (authorization, userInfo) <- delegate.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri)
      loggedUser <- loggedUserFunction(
        LoggedUserFunctionParameters(AccessTokenData.empty, () => Future.successful(userInfo))
      )
    } yield (authorization, loggedUser)
  }

  override def introspectAccessToken(accessToken: String): Future[AccessTokenData] =
    delegate.introspectAccessToken(accessToken)

  override def obtainUserInfo(accessToken: String, accessTokenData: AccessTokenData): Future[AuthenticatedUser] = {
    loggedUserFunction(
      LoggedUserFunctionParameters(accessTokenData, () => delegate.obtainUserInfo(accessToken, accessTokenData))
    )
  }

}

case class LoggedUserFunctionParameters[UserInfoData](
    accessTokenData: AccessTokenData,
    getUserInfo: () => Future[UserInfoData]
)
