package pl.touk.nussknacker.ui.security

import java.io.File

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import net.ceedubs.ficus.readers.EnumerationReader._
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import BasicHttpAuthenticator._
import org.mindrot.jbcrypt.BCrypt
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BasicHttpAuthenticator(usersList: List[ConfiguredUser]) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] {

  //TODO: config reload
  private val users = prepareUsers()

  def apply(credentials: Credentials): Future[Option[LoggedUser]] = Future {
    authorize(credentials)
  }

  private[security] def authorize(credentials: Credentials): Option[LoggedUser] = {
    credentials match {
      case d@Provided(id) => users
        .get(id)
        .filter(u => d.verify(u.password.value, hash(u)))
        .map(_.toLoggedUser)
      case _ => None
    }
  }

  private def hash(u: UserWithPassword)(receivedSecret: String): String = {
    u.password match {
      case PlainPassword(_) => receivedSecret
      case EncryptedPassword(encryptedPassword) =>
        // it uses salting strategy which is saved on the beginning of encryptedPassword
        BCrypt.hashpw(receivedSecret, encryptedPassword)
    }
  }

  private def prepareUsers(): Map[String, UserWithPassword] = {
    usersList.map { u =>
      val password = (u.password, u.encryptedPassword) match {
        case (Some(plain), None) => PlainPassword(plain)
        case (None, Some(encrypted)) => EncryptedPassword(encrypted)
        case (Some(_), Some(_)) => throw new IllegalStateException("Specified both password and encrypted password for user: " + u.id)
        case (None, None) => throw new IllegalStateException("Neither specified password nor encrypted password for user: " + u.id)
      }
      u.id -> UserWithPassword(u.id, password, u.categoryPermissions, u.globalPermissions, u.isAdmin)
    }.toMap
  }

}

object BasicHttpAuthenticator {

  def apply(path: String): BasicHttpAuthenticator =
    BasicHttpAuthenticator(ConfigFactory.parseFile(new File(path)))

  def apply(config: Config): BasicHttpAuthenticator =
    new BasicHttpAuthenticator(config.as[List[ConfiguredUser]]("users"))


  private[security] case class ConfiguredUser(id: String,
                                              password: Option[String],
                                              encryptedPassword: Option[String],
                                              categoryPermissions: Map[String, Set[Permission]] = Map.empty,
                                              globalPermissions: List[GlobalPermission] = Nil,
                                              isAdmin: Boolean = false)

  private sealed trait Password {
    def value: String
  }

  private case class PlainPassword(value: String) extends Password

  private case class EncryptedPassword(value: String) extends Password

  private case class UserWithPassword(id: String, password: Password, categoryPermissions: Map[String, Set[Permission]], globalPermissions: List[GlobalPermission], isAdmin: Boolean) {
    def toLoggedUser = LoggedUser(id, categoryPermissions, globalPermissions, isAdmin)
  }

}