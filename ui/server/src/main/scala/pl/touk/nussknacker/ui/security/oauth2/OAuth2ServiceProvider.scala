package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Permission

import scala.concurrent.Future

object OAuth2ServiceProvider extends LazyLogging {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader): OAuth2Service = {
    val service = ScalaServiceLoader.loadClass[OAuth2ServiceFactory](classLoader) {
      DefaultOAuth2ServiceFactory()
    }

    logger.info(s"Loaded OAuth2Service: $service.")

    service.create(configuration)
  }

  trait OAuth2Service {
    def authenticate(code: String): Future[OAuth2AuthenticateData]
    def profile(token: String): Future[OAuth2Profile]
  }

  trait OAuth2ServiceFactory {
    def create(configuration: OAuth2Configuration): OAuth2Service
  }

  case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])

  case class OAuth2Profile(id: String,
                           email: String,
                           isAdmin: Boolean,
                           permissions: Map[String, Set[Permission]] = Map.empty,
                           accesses: List[GlobalPermission] = List.empty) {

    def toLoggedUser(): LoggedUser = LoggedUser(
      id = this.id,
      isAdmin = this.isAdmin,
      categoryPermissions = this.permissions,
      globalPermissions = this.accesses
    )
  }
}
