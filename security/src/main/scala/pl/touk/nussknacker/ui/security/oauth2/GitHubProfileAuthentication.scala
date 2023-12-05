package pl.touk.nussknacker.ui.security.oauth2

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.restmodel.security.AuthenticatedUser

import scala.concurrent.{ExecutionContext, Future}

@JsonCodec final case class GitHubProfileResponse(id: Long, email: Option[String], login: String)

class GitHubProfileAuthentication(configuration: OAuth2Configuration)
    extends AuthenticationStrategy[GitHubProfileResponse] {

  override def authenticateUser(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[GitHubProfileResponse]
  )(implicit ec: ExecutionContext): Future[AuthenticatedUser] = {
    getProfile.map { profile =>
      val userRoles = configuration.getUserRoles(profile.login)
      val username  = profile.login
      AuthenticatedUser(id = profile.id.toString, username = username, userRoles)
    }
  }

}
