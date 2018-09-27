package pl.touk.nussknacker.ui.security

import java.io.File

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import net.ceedubs.ficus.readers.EnumerationReader._
import pl.touk.nussknacker.ui.security.api.Permission.Permission

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BasicHttpAuthenticator(path: String) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] {
  //TODO: config reload
  private val users = prepareUsers()

  def apply(credentials: Credentials): Future[Option[LoggedUser]] = Future {
    credentials match {
      case d@Provided(id) => users
        .get(id)
        .filter(u => d.verify(u.password))
        .map(_.toLoggedUser)
      case _ => None
    }
  }

  private def prepareUsers(): Map[String, LoggedUserWithPassword] = {
    val config = ConfigFactory.parseFile(new File(path))
    config.as[List[LoggedUserWithPassword]]("users").map(u => u.id -> u).toMap
  }

  private case class LoggedUserWithPassword(
                                             id: String,
                                             password: String,
                                             categoryPermissions: Map[String, Set[Permission]]) {
    def toLoggedUser = LoggedUser(id, categoryPermissions)
  }
  
}
