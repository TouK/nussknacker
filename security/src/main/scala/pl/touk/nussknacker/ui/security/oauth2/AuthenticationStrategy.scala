package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationStrategy[ProfileResponse] {
  def authenticateUser(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[ProfileResponse]
  )(implicit ec: ExecutionContext): Future[AuthenticatedUser]
}

object AuthenticationStrategy {
  def getUserRoles(
      identity: String,
      configuration: OAuth2Configuration,
      defaults: Set[String] = Set.empty
  ): Set[String] =
    configuration
      .findUserById(identity)
      .map(_.roles ++ defaults)
      .getOrElse(defaults)

  def usernameBasedOnUsersConfiguration(identity: String, configuration: OAuth2Configuration): Option[String] =
    configuration.findUserById(identity).flatMap(_.username)

}