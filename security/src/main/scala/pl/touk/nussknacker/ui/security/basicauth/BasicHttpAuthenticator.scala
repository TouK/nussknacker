package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import org.mindrot.jbcrypt.BCrypt
import pl.touk.nussknacker.engine.util.cache.DefaultCache
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.basicauth.BasicHttpAuthenticator.{EncryptedPassword, PlainPassword, UserWithPassword}

import scala.concurrent.{ExecutionContext, Future}

class BasicHttpAuthenticator(configuration: BasicAuthenticationConfiguration) extends SecurityDirectives.AsyncAuthenticator[AuthenticatedUser] {
  //If we want use always reloaded config then we need just prepareUsers()
  private val users = prepareUsers()

  private val hashesCache =
    configuration.cachingHashesOrDefault.toCacheConfig.map(new DefaultCache[(String, String), String](_))

  def apply(credentials: Credentials): Future[Option[AuthenticatedUser]] = Future.successful {
    credentials match {
      case d@Provided(id) => authenticate(d)
      case _ => None
    }
  }

  private[basicauth] def authenticate(prov: Provided): Option[AuthenticatedUser] = {
    users
      .get(prov.identifier)
      .filter(us => prov.verify(us.password.value, hash(us)))
      .map(user => AuthenticatedUser(user.identity, user.identity, user.roles))
  }

  private def hash(u: UserWithPassword)(receivedSecret: String): String = {
    u.password match {
      case PlainPassword(_) => receivedSecret
      case EncryptedPassword(encryptedPassword) =>
        def doComputeHash(): String = computeBCryptHash(receivedSecret, encryptedPassword)
        hashesCache
          .map(_.getOrCreate((receivedSecret, encryptedPassword))(doComputeHash()))
          .getOrElse(doComputeHash())
    }
  }

  protected def computeBCryptHash(receivedSecret: String, encryptedPassword: String): String = {
    // it uses salting strategy which is saved on the beginning of encryptedPassword
    BCrypt.hashpw(receivedSecret, encryptedPassword)
  }

  private def prepareUsers(): Map[String, UserWithPassword] = {
    configuration.users.map { u =>
      val password = (u.password, u.encryptedPassword) match {
        case (Some(plain), None) => PlainPassword(plain)
        case (None, Some(encrypted)) => EncryptedPassword(encrypted)
        case (Some(_), Some(_)) => throw new IllegalStateException("Specified both password and encrypted password for user: " + u.identity)
        case (None, None) => throw new IllegalStateException("Neither specified password nor encrypted password for user: " + u.identity)
      }
      u.identity -> UserWithPassword(u.identity, password, u.roles)
    }.toMap
  }
}

object BasicHttpAuthenticator {
  def apply(config: BasicAuthenticationConfiguration): BasicHttpAuthenticator = new BasicHttpAuthenticator(config)

  private sealed trait Password {
    def value: String
  }

  private case class PlainPassword(value: String) extends Password
  private case class EncryptedPassword(value: String) extends Password
  private case class UserWithPassword(identity: String, password: Password, roles: Set[String])
}