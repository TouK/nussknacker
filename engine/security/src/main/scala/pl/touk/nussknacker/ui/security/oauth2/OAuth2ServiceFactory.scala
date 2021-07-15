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
  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, Option[UserInfoData])]
  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])]
}


trait OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] =
    throw new NotImplementedError("Trying to use the new version of the interface, which is not implemented yet")
}