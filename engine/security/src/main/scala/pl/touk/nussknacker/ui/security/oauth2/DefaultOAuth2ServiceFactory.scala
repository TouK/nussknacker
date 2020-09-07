package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.oauth2.DefaultOAuth2ServiceFactoryWithProfileFormat.GetLoggedUserType
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.{Auth0ProfileResponse, DefaultAccessTokenResponse, GitHubProfileResponse}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DefaultOAuth2Service[ProfileResponse: Decoder](clientApi: OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse],
                                                     configuration: OAuth2Configuration,
                                                     allCategories: List[String])(implicit getLoggedUser: GetLoggedUserType[ProfileResponse]) extends OAuth2Service with LazyLogging {
  def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  def authorize(token: String): Future[LoggedUser] = clientApi.profileRequest(token).map(getLoggedUser(_, configuration, allCategories))
}

class DefaultOAuth2ServiceFactoryWithProfileFormat[ProfileResponse: Decoder](implicit getLoggedUser: GetLoggedUserType[ProfileResponse]) {
  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](new OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), configuration, allCategories)
}

object DefaultOAuth2ServiceFactoryWithProfileFormat {
  type GetLoggedUserType[ProfileResponse] = (ProfileResponse, OAuth2Configuration, List[String]) => LoggedUser

  val defaultUserRole = "User"

  def apply(configuration: OAuth2Configuration, allCategories: List[String]) = {
    import implicits._
    configuration.profileFormat match {
      case ProfileFormat.GITHUB => new DefaultOAuth2ServiceFactoryWithProfileFormat[GitHubProfileResponse]()
      case ProfileFormat.AUTH0 => new DefaultOAuth2ServiceFactoryWithProfileFormat[Auth0ProfileResponse]()
    }
  }

  def getUserRoles(email: Option[String], configuration: OAuth2Configuration, defaults: List[String] = List(defaultUserRole)): List[String] =
    configuration
      .users
      .find(us => email.exists(_.toLowerCase.equals(us.identity.toLowerCase)))
      .map(_.roles ++ defaults)
      .getOrElse(defaults)

  object implicits {
    implicit val gitHubGetLoggedUser: GetLoggedUserType[GitHubProfileResponse] = (profile, configuration, allCategories) => {
      val userRoles = getUserRoles(profile.email, configuration)
      val rulesSet = RulesSet.getOnlyMatchingRules(userRoles, configuration.rules, allCategories)
      val username = profile.login.getOrElse(profile.id.toString)
      LoggedUser(id = profile.id.toString, username = username, rulesSet = rulesSet)
    }

    implicit val auth0GetLoggedUser: GetLoggedUserType[Auth0ProfileResponse] = (profile, configuration, allCategories) => {
      val userRoles = getUserRoles(profile.email, configuration)
      val rulesSet = RulesSet.getOnlyMatchingRules(userRoles, configuration.rules, allCategories)
      val username = profile.username.getOrElse(profile.sub)
      LoggedUser(id = profile.sub, username = username, rulesSet = rulesSet)
    }
  }
}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactory.defaultService(configuration, allCategories)
}

object DefaultOAuth2ServiceFactory extends {
  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory

  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).defaultService(configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).defaultService(configuration, allCategories)
}
