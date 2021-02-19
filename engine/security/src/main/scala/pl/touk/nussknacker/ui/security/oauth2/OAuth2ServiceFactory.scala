package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

trait OAuth2AuthorizationData {
  val accessToken: String
  val expirationPeriod: Option[FiniteDuration]
  val tokenType: String
  val refreshToken: Option[String]
}

trait OAuth2NewService[+UserInfoData, +AuthorizationData <: OAuth2AuthorizationData] {
  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, Option[UserInfoData])]
  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])]
}

// uncomment when we drop Scala 2.11
//@deprecatedInheritance("extend OAuth2NewService")
trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def authorize(token: String): Future[LoggedUser]
}

trait OAuth2ServiceFactory {
  def createNew(configuration: OAuth2Configuration, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2NewService[LoggedUser, OAuth2AuthorizationData] =
    throw new NotImplementedError("Trying to use the new version of the interface, which is not implemented yet")
  // uncomment when we drop Scala 2.11
  //@deprecatedOverriding("override createNew")
  def create(configuration: OAuth2Configuration, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service = {
    import OAuth2ServiceFactory.BackwardCompatibleOAuth2ServiceWrapper
    createNew(configuration, allCategories)
  }
}

object OAuth2ServiceFactory {
  implicit class BackwardCompatibleOAuth2ServiceWrapper(service: OAuth2NewService[LoggedUser, OAuth2AuthorizationData])(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]) extends OAuth2Service {
    override def authenticate(code: String): Future[OAuth2AuthenticateData] =
      service.obtainAuthorizationAndUserInfo(code).map { case (authorization, _) =>
        OAuth2AuthenticateData(
          access_token = authorization.accessToken,
          token_type = authorization.tokenType,
          refresh_token = authorization.refreshToken
        )
      }

    override def authorize(token: String): Future[LoggedUser] =
      service.checkAuthorizationAndObtainUserinfo(token).map { case (loggedUser, _) => loggedUser }
  }
}

case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])
