package pl.touk.nussknacker.ui.security

import java.io.File

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, LoggedUser}


class SimpleAuthenticator(path: String) extends SecurityDirectives.Authenticator[LoggedUser] {
  //TODO: config reload
  private val users = prepareUsers()

  def prepareUsers(): Map[String, LoggedUser] = {
    val config = ConfigFactory.parseFile(new File(path))
    config.as[List[LoggedUser]]("users").map(u => u.id -> u).toMap
  }

  override def apply(credentials: Credentials): Option[LoggedUser] = credentials match {
    case d@Provided(id) => users.get(id).filter(u => d.verify(u.password))
    case _ => None
  }
}
case class SimpleAuthenticatorFactory() extends AuthenticatorFactory{
  override def createAuthenticator(config: Config) = new SimpleAuthenticator(config.getString("usersFile"))
}



