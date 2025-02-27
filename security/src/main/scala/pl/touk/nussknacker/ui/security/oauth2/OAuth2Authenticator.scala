package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import akka.http.scaladsl.server.directives.Credentials.Provided
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.util.SensitiveDataMasker
import pl.touk.nussknacker.engine.util.SensitiveDataMasker.JsonMasker
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import pl.touk.nussknacker.ui.security.oauth2.jwt.{ParsedJwtToken, RawJwtToken}
import sttp.client3.SttpBackend

import java.security.Key
import scala.concurrent.{ExecutionContext, Future}

class OAuth2Authenticator(service: OAuth2Service[AuthenticatedUser, _])(
    implicit ec: ExecutionContext,
    sttpBackend: SttpBackend[Future, Any]
) extends SecurityDirectives.AsyncAuthenticator[AuthenticatedUser]
    with LazyLogging {

  def apply(credentials: Credentials): Future[Option[AuthenticatedUser]] =
    authenticate(credentials)

  private[security] def authenticate(credentials: Credentials): Future[Option[AuthenticatedUser]] = {
    credentials match {
      case Provided(token) => authenticate(token)
      case _               => Future.successful(Option.empty)
    }
  }

  private[oauth2] def authenticate(token: String): Future[Option[AuthenticatedUser]] =
    service.checkAuthorizationAndAuthenticateUser(token).map(prf => Option(prf._1)).recover {
      case OAuth2ErrorHandler(ex) =>
        logger.debug("Access token rejected:", ex)
        Option.empty // Expired or non-exists token - user not authenticated
    }

}

object OAuth2Authenticator extends LazyLogging {

  def apply(
      service: OAuth2Service[AuthenticatedUser, _]
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): OAuth2Authenticator =
    new OAuth2Authenticator(service)

}

object OAuth2ErrorHandler {

  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(apply)

  def apply(t: Throwable): Boolean = t match {
    case OAuth2CompoundException(errors) => errors.toList.collectFirst { case e @ OAuth2ServerError(_) => e }.isEmpty
    case _                               => false
  }

  trait OAuth2Error {
    def msg: String
  }

  final case class OAuth2CompoundException(errors: NonEmptyList[OAuth2Error]) extends Exception {
    override def getMessage: String =
      errors.toList.mkString("OAuth2 exception with the following errors:\n - ", "\n - ", "")
  }

  trait OAuth2JwtError extends OAuth2Error

  final case class OAuth2JwtDecodeRawError(rawToken: RawJwtToken, cause: Throwable) extends OAuth2JwtError {
    override def msg: String = s"Failure in jwt decode: ${cause.getLocalizedMessage}. Token: ${rawToken.masked}"
  }

  final case class OAuth2JwtKeyDetermineError(token: ParsedJwtToken, cause: Throwable) extends OAuth2JwtError {
    override def msg: String = s"Failure in key determining: ${cause.getLocalizedMessage}. Token: ${token.masked}"
  }

  final case class OAuth2JwtDecodeClaimsError(token: ParsedJwtToken, key: Key, cause: Throwable)
      extends OAuth2JwtError {
    override def msg: String =
      s"Failure in decoding json using key: ${cause.getLocalizedMessage}. Token: ${token.masked}"
  }

  final case class OAuth2JwtDecodeClaimsJsonError(
      token: ParsedJwtToken,
      key: Key,
      tokenClaimsJson: Json,
      cause: Throwable
  ) extends OAuth2JwtError {
    override def msg: String =
      s"Failure in decoding token claims: ${mask(cause)}. Token: ${token.masked}, token claims: ${tokenClaimsJson.masked.noSpaces}"
  }

  private def mask(cause: Throwable) = {
    cause.getLocalizedMessage.replaceAll("Got value '.*'", s"Got value '${SensitiveDataMasker.placeholder}'")
  }

  final case class OAuth2AuthenticationRejection(msg: String) extends OAuth2Error

  final case class OAuth2AccessTokenRejection(msg: String) extends OAuth2Error

  final case class OAuth2ServerError(msg: String) extends OAuth2Error
}
