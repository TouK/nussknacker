package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Authenticator(configuration: OAuth2Configuration, service: OAuth2Service[AuthenticatedUser, _])
                         (implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends SecurityDirectives.AsyncAuthenticator[AuthenticatedUser] with LazyLogging {
  def apply(credentials: Credentials): Future[Option[AuthenticatedUser]] =
    authenticate(credentials)

  private[security] def authenticate(credentials: Credentials): Future[Option[AuthenticatedUser]] = {
    credentials match {
      case Provided(token) => authenticate(token)
      case _ => Future.successful(Option.empty)
    }
  }

  private[oauth2] def authenticate(token: String): Future[Option[AuthenticatedUser]] =
    service.checkAuthorizationAndObtainUserinfo(token).map(prf => Option(prf._1)).recover {
      case OAuth2ErrorHandler(_) => Option.empty // Expired or non-exists token - user not authenticated
    }
}

object OAuth2Authenticator extends LazyLogging {
  def apply(configuration: OAuth2Configuration, service: OAuth2Service[AuthenticatedUser, _])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Authenticator =
    new OAuth2Authenticator(configuration, service)
}

object OAuth2ErrorHandler {

  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(apply)

  def apply(t: Throwable): Boolean = t match {
    case OAuth2CompoundException(errors) => errors.toList.collectFirst { case e@OAuth2ServerError(_) => e }.isEmpty
    case _ => false
  }

  trait OAuth2Error {
    def msg: String
  }

  case class OAuth2CompoundException(errors: NonEmptyList[OAuth2Error]) extends Exception {
    override def getMessage: String = errors.toList.mkString("OAuth2 exception with the following errors:\n - ", "\n - ", "")
  }

  case class OAuth2JwtError(msg: String) extends OAuth2Error

  case class OAuth2AuthenticationRejection(msg: String) extends OAuth2Error

  case class OAuth2AccessTokenRejection(msg: String) extends OAuth2Error

  case class OAuth2ServerError(msg: String) extends OAuth2Error
}
