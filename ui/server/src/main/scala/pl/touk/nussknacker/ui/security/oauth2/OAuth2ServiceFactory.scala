package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.{DefaultAccessTokenResponse, DefaultProfileResponse}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OAuth2Service {
  def authenticate(code: String): Future[OAuth2AuthenticateData]
  def profile(token: String): Future[OAuth2Profile]
}

class DefaultOAuth2Service(clientApi: OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map{ resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  override def profile(token: String): Future[OAuth2Profile] = {
    clientApi.profileRequest(token).map{ profile =>
      val roles = OAuth2ServiceFactory.getUserRoles(profile.email, configuration, List.apply(OAuth2ServiceFactory.defaultUserRole))
      OAuth2Profile(
        id = profile.id.toString,
        email = profile.email,
        isAdmin = OAuth2AuthenticatorFactory.isAdmin(roles, configuration.rules),
        permissions = OAuth2AuthenticatorFactory.getPermissions(roles, configuration.rules),
        accesses = OAuth2AuthenticatorFactory.getGlobalPermissions(roles, configuration.rules),
        roles = roles
      )
    }
  }
}

object OAuth2ServiceFactory {
  val defaultUserRole = "User"

  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader): OAuth2Service = ScalaServiceLoader.loadClass[OAuth2Service](classLoader) {
    val clientApi = OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration)
    new DefaultOAuth2Service(clientApi, configuration)
  }

  def apply(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): DefaultOAuth2Service
    = new DefaultOAuth2Service(new OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration), configuration)

  def getUserRoles(email: String, configuration: OAuth2Configuration, defaults: List[String] = List.empty): List[String] =
    configuration.users.find(_.email.equals(email)).map(_.roles ++ defaults).getOrElse(defaults)

  final case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])

  final case class OAuth2Profile(id: String,
                                 email: String,
                                 isAdmin: Boolean,
                                 permissions: Map[String, Set[Permission]] = Map.empty,
                                 accesses: List[GlobalPermission] = List.empty,
                                 roles: List[String] = Nil) {

    def toLoggedUser(): LoggedUser = LoggedUser(
      id = this.id,
      isAdmin = this.isAdmin,
      categoryPermissions = this.permissions,
      globalPermissions = this.accesses
    )
  }
}