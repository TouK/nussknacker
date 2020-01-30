package pl.touk.nussknacker.ui.security.api

import java.io.File
import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.{ConfigRule, ConfigUser}
import pl.touk.nussknacker.ui.security.api.AuthenticationMethod.AuthenticationMethod
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

trait AuthenticationConfiguration {
  def authorizeUrl: Option[URI] = Option.empty
  def method: AuthenticationMethod
  def usersFile: String

  val userConfig: Config = ConfigFactory.parseFile(new File(usersFile))

  lazy val users: List[ConfigUser] = AuthenticationConfiguration.getUsers(userConfig)

  lazy val rules: List[ConfigRule] = AuthenticationConfiguration.getRules(userConfig)
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
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  val authenticationConfigPath = "authentication"
  val methodConfigPath = s"$authenticationConfigPath.method"
  val usersConfigurationPath = "users"
  val rulesConfigurationPath = "rules"

  def parseMethod(config: Config): AuthenticationMethod = config.as[AuthenticationMethod](methodConfigPath)

  def getUsers(config: Config): List[ConfigUser] = config.as[List[ConfigUser]](usersConfigurationPath)

  def getRules(config: Config): List[ConfigRule] = config.as[List[ConfigRule]](rulesConfigurationPath)

  case class ConfigUser(identity: String,
                        password: Option[String],
                        encryptedPassword: Option[String],
                        roles: List[String])

  case class ConfigRule(role: String,
                        isAdmin: Boolean = false,
                        categories: List[String] = List.empty,
                        permissions: List[Permission] = List.empty,
                        globalPermissions: List[GlobalPermission] = List.empty)
}

case class DefaultAuthenticationConfiguration(method: AuthenticationMethod = AuthenticationMethod.Other, usersFile: String) extends AuthenticationConfiguration

object DefaultAuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import AuthenticationConfiguration._

  def create(config: Config): DefaultAuthenticationConfiguration =
    config.as[DefaultAuthenticationConfiguration](authenticationConfigPath)
}