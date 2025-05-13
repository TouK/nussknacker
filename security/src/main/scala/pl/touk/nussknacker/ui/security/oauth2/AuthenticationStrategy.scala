package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationStrategy[ProfileResponse] {

  def authenticateUser(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[ProfileResponse]
  )(implicit ec: ExecutionContext): Future[AuthenticatedUser]

}
