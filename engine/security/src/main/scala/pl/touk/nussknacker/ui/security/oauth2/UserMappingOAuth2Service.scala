package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

class UserMappingOAuth2Service[UserInfoData: Decoder, AuthorizationData <: OAuth2AuthorizationData : Decoder]
(
  delegate: OAuth2NewService[UserInfoData, AuthorizationData],
  loggedUserFunction: UserInfoData => LoggedUser
)
(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT])
  extends OAuth2NewService[LoggedUser, AuthorizationData] {

  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, Option[LoggedUser])] =
    delegate.obtainAuthorizationAndUserInfo(authorizationCode).map { case (authorization, userInfo) =>
      (authorization, userInfo.map(loggedUserFunction))
    }

  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(LoggedUser, Option[Deadline])] =
    delegate.checkAuthorizationAndObtainUserinfo(accessToken).map { case (userInfo, expiration) =>
      (loggedUserFunction(userInfo), expiration)
    }
}
