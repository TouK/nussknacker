package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

class UserMappingOAuth2Service[UserInfoData: Decoder, AuthorizationData <: OAuth2AuthorizationData : Decoder]
(
  delegate: OAuth2Service[UserInfoData, AuthorizationData],
  loggedUserFunction: UserInfoData => AuthenticatedUser
)
(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT])
  extends OAuth2Service[AuthenticatedUser, AuthorizationData] {

  def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(AuthorizationData, AuthenticatedUser)] =
    delegate.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri).map { case (authorization, userInfo) =>
      (authorization, loggedUserFunction(userInfo))
    }

  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(AuthenticatedUser, Option[Deadline])] =
    delegate.checkAuthorizationAndObtainUserinfo(accessToken).map { case (userInfo, expiration) =>
      (loggedUserFunction(userInfo), expiration)
    }
}
