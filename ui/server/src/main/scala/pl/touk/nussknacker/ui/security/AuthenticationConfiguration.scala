package pl.touk.nussknacker.ui.security

import java.net.URI

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthConfiguration
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

import scala.util.{Failure, Success, Try}

trait AuthenticationConfiguration {
  def getAuthenticationRedirectUrl(): Option[URI] = Option.empty
  def getBackend(): AuthenticationBackend.Value
}

object AuthenticationConfiguration extends LazyLogging {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  implicit val uriValueReader: ValueReader[URI] = new ValueReader[URI] {
    def read(config: Config, path: String): URI = new URI(config.getString(path))
  }

  private final val authenticationConfigPath = "authentication"

  def apply(config: Config): AuthenticationConfiguration = {
    val backend = config.as[Option[AuthenticationBackend.Value]](s"$authenticationConfigPath.backend")
    
    getAuthenticationConfig(backend, config) match {
      case Success(authenticationConfig) => authenticationConfig
      case Failure(e) => throw e
    }
  }

  private [security] def getAuthenticationConfig(backend: Option[AuthenticationBackend.Value], config: Config): Try[AuthenticationConfiguration] = {
    backend match {
      case Some(AuthenticationBackend.BasicAuth) => Success(config.as[BasicAuthConfiguration](authenticationConfigPath))
      case Some(AuthenticationBackend.OAuth2) => Success(config.as[OAuth2Configuration](authenticationConfigPath))
      case None => Success(config.as[BasicAuthConfiguration](authenticationConfigPath))
      case _ => Failure(new IllegalArgumentException(s"Unsupported authorization backend: $backend."))
    }
  }
}