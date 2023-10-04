package pl.touk.nussknacker.ui.security.oauth2

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Profile.getUserRoles

import scala.concurrent.{ExecutionContext, Future}

@JsonCodec final case class GitHubProfileResponse(id: Long, email: Option[String], login: String)

object GitHubProfile extends OAuth2Profile[GitHubProfileResponse] {
  override def authenticateUser(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[GitHubProfileResponse],
      configuration: OAuth2Configuration
  )(implicit ec: ExecutionContext): Future[AuthenticatedUser] = {
    getProfile.map { profile =>
      val userRoles = getUserRoles(profile.login, configuration)
      val username  = profile.login
      AuthenticatedUser(id = profile.id.toString, username = username, userRoles)
    }
  }
}
