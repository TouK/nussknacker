package pl.touk.nussknacker.ui.security.api

import java.net.URI

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.{ConfigRule, ConfigUser}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

trait AuthenticationConfiguration {
  def name: String
  def usersFile: URI

  val userConfig: Config = ConfigFactoryExt.parseUri(usersFile)

  lazy val users: List[ConfigUser] = AuthenticationConfiguration.getUsers(userConfig)

  lazy val rules: List[ConfigRule] = AuthenticationConfiguration.getRules(userConfig)
}

object AuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  val authenticationConfigPath = "authentication"
  val methodConfigPath = s"$authenticationConfigPath.method"
  val usersConfigurationPath = "users"
  val rulesConfigurationPath = "rules"

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
