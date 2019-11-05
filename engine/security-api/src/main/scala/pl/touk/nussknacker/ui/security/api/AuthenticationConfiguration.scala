package pl.touk.nussknacker.ui.security.api

import java.io.File
import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.security.api.AuthenticationMethod.AuthenticationMethod
import pl.touk.nussknacker.ui.security.api.DefaultAuthenticationConfiguration.DefaultConfigUser
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

trait AuthenticationConfiguration {
  def authorizeUrl: Option[URI] = Option.empty
  def method: AuthenticationMethod
}

object AuthenticationMethod extends Enumeration {
  type AuthenticationMethod = Value

  val BasicAuth = Value("BasicAuth")
  val OAuth2 = Value("OAuth2")
  val Other = Value("Other")
}

object AuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.EnumerationReader._

  implicit val uriValueReader: ValueReader[URI] = new ValueReader[URI] {
    def read(config: Config, path: String): URI = new URI(config.getString(path))
  }

  val authenticationConfigPath = "authentication"
  val methodConfigPath = s"$authenticationConfigPath.method"
  val usersConfigurationPath = "users"
  val rulesConfigurationPath = "rules"

  def parseMethod(config: Config): AuthenticationMethod = config.as[AuthenticationMethod](methodConfigPath)
}

case class DefaultAuthenticationConfiguration(method: AuthenticationMethod = AuthenticationMethod.Other, usersFile: String) extends AuthenticationConfiguration with LazyLogging {
  lazy val users: List[DefaultConfigUser] = DefaultAuthenticationConfiguration.getUsers(ConfigFactory.parseFile(new File(usersFile)))
}

object DefaultAuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import AuthenticationConfiguration._

  def create(config: Config): DefaultAuthenticationConfiguration = config.as[DefaultAuthenticationConfiguration](authenticationConfigPath)

  def getUsers(config: Config): List[DefaultConfigUser] = config.as[List[DefaultConfigUser]](usersConfigurationPath)

  case class DefaultConfigUser(id: String, password: Option[String],
                               encryptedPassword: Option[String],
                               categoryPermissions: Map[String, Set[Permission]] = Map.empty,
                               globalPermissions: List[GlobalPermission] = List.empty,
                               isAdmin: Boolean = false)
}