package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import org.mindrot.jbcrypt.BCrypt
import pl.touk.nussknacker.engine.util.cache.DefaultCache
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.basicauth.BasicHttpAuthenticator.{EncryptedPassword, PlainPassword, UserWithPassword}

import scala.concurrent.{ExecutionContext, Future}

class BasicHttpAuthenticator(configuration: BasicAuthenticationConfiguration, allCategories: List[String]) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] {
  //If we want use always reloaded config then we need just prepareUsers()
  private val users = prepareUsers()

  private val hashesCache =
    configuration.cachingHashesOrDefault.toCacheConfig.map(new DefaultCache[(String, String), String](_))

  def apply(credentials: Credentials): Future[Option[LoggedUser]] = Future.successful {
    credentials match {
      case d@Provided(id) => authenticate(d)
      case _ => None
    }
  }

  private[basicauth] def authenticate(prov: Provided): Option[LoggedUser] = {
    users
      .get(prov.identifier)
      .filter(us => prov.verify(us.password.value, hash(us)))
      .map(toLoggedUser)
  }

  private[basicauth] def toLoggedUser(user: UserWithPassword): LoggedUser = {
    val rulesSet = RulesSet.getOnlyMatchingRules(user.roles, configuration.rules, allCategories)
    LoggedUser(id = user.identity, username = user.identity, rulesSet = rulesSet)
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
  def apply(config: BasicAuthenticationConfiguration, allCategories: List[String]): BasicHttpAuthenticator = new BasicHttpAuthenticator(config, allCategories)

  private sealed trait Password {
    def value: String
  }

  private case class PlainPassword(value: String) extends Password
  private case class EncryptedPassword(value: String) extends Password
  private case class UserWithPassword(identity: String, password: Password, roles: List[String])
}