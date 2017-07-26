package pl.touk.nussknacker.ui.security

import java.io.File

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.security.Permission.Permission
import net.ceedubs.ficus.readers.EnumerationReader._

class SimpleAuthenticator(path: String) extends SecurityDirectives.Authenticator[LoggedUser] {

  //TODO: config reload
  val users = prepareUsers()

  def prepareUsers() : Map[String, LoggedUser] = {
    val config = ConfigFactory.parseFile(new File(path))
    config.as[List[LoggedUser]]("users").map(u => u.id -> u).toMap
  }

  override def apply(credentials: Credentials) = credentials match {
    case d@Provided(id) => users.get(id).filter(u => d.verify(u.password))
    case _ => None
  }
}

case class LoggedUser(id: String, password: String, permissions: List[Permission],
                      categories: List[String]) {
  def hasPermission(permission: Permission) = {
    permissions.contains(permission) || isAdmin
  }

  def isAdmin = permissions.contains(Permission.Admin)
}

object Permission extends Enumeration {
  type Permission = Value
  val Read, Write, Deploy, Admin = Value
}

