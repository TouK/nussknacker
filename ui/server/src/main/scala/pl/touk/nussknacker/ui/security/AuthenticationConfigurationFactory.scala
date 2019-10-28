package pl.touk.nussknacker.ui.security

import java.io.File
import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.security.AuthenticationConfigurationFactory.DefaultConfigUser
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthConfiguration
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

import scala.util.{Failure, Success, Try}

trait AuthenticationConfiguration {
  def authorizeUrl: Option[URI] = Option.empty
  def backend: AuthenticationBackend.Value
}

case class DefaultAuthenticationConfiguration(backend: AuthenticationBackend.Value, usersFile: String) extends AuthenticationConfiguration with LazyLogging {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  def loadUsers(): List[DefaultConfigUser]
    = loadUsersConfig().as[List[DefaultConfigUser]](AuthenticationConfigurationFactory.usersConfigurationPath)

  def loadUsersConfig(): Config
    = ConfigFactory.parseFile(new File(usersFile))
}

object AuthenticationConfigurationFactory extends LazyLogging {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val usersConfigurationPath = "users"
  val rulesConfigurationPath = "rules"

  implicit val uriValueReader: ValueReader[URI] = new ValueReader[URI] {
    def read(config: Config, path: String): URI = new URI(config.getString(path))
  }

  final val backendConfigPath = "authentication.backend"
  final val authenticationConfigPath = "authentication"

  def apply(config: Config): AuthenticationConfiguration = {
    val backend = config.as[Option[AuthenticationBackend.Value]](s"$authenticationConfigPath.backend")

    getAuthenticationConfig(backend, config) match {
      case Success(authenticationConfig) => authenticationConfig
      case Failure(e) => throw e
    }
  }

  def getBackendType(config: Config): AuthenticationBackend.Value
    = config.as[AuthenticationBackend.Value]("authentication.backend")

  def oAuth2Config(config: Config): OAuth2Configuration
    = config.as[OAuth2Configuration](authenticationConfigPath)

  def basicAuthConfig(config: Config): BasicAuthConfiguration
    = config.as[BasicAuthConfiguration](authenticationConfigPath)

  def getBackendType(config: AuthenticationConfiguration): AuthenticationBackend.Value =
    config match {
      case _ : BasicAuthConfiguration => AuthenticationBackend.BasicAuth
      case _ : OAuth2Configuration => AuthenticationBackend.OAuth2
      case _ => AuthenticationBackend.Unknown
    }

  private [security] def getAuthenticationConfig(backend: Option[AuthenticationBackend.Value], config: Config): Try[AuthenticationConfiguration] = {
    backend match {
      case Some(AuthenticationBackend.BasicAuth) => Success(config.as[BasicAuthConfiguration](authenticationConfigPath))
      case Some(AuthenticationBackend.OAuth2) => Success(config.as[OAuth2Configuration](authenticationConfigPath))
      case None => Success(config.as[DefaultAuthenticationConfiguration](authenticationConfigPath))
      case _ => Failure(new IllegalArgumentException(s"Unsupported authorization backend: $backend."))
    }
  }

  case class DefaultConfigUser(id: String,
                               password: Option[String],
                               encryptedPassword: Option[String],
                               categoryPermissions: Map[String, Set[Permission]] = Map.empty,
                               globalPermissions: List[GlobalPermission] = List.empty,
                               isAdmin: Boolean = false)
}