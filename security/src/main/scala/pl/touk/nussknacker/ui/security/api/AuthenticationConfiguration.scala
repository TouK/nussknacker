package pl.touk.nussknacker.ui.security.api

import java.net.URI
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigUser
import GlobalPermission.GlobalPermission
import pl.touk.nussknacker.security.Permission.Permission

trait AuthenticationConfiguration {
  def name: String
  def usersFile: URI

  val userConfig: Config = ConfigFactoryExt.parseUri(usersFile, getClass.getClassLoader)

  lazy val users: List[ConfigUser] = AuthenticationConfiguration
    .getUsers(userConfig)
    .getOrElse(
      throw new IllegalArgumentException(
        s"Missing field ${AuthenticationConfiguration.usersConfigurationPath} at ${userConfig.getConfig(AuthenticationConfiguration.usersConfigPath)} users config file."
      )
    )

}

object AuthenticationConfiguration {

  import net.ceedubs.ficus.readers.EnumerationReader._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  val authenticationConfigPath = "authentication"
  val methodConfigPath         = s"$authenticationConfigPath.method"
  val usersConfigPath          = s"$authenticationConfigPath.usersFile"
  val usersConfigurationPath   = "users"
  val rulesConfigurationPath   = "rules"

  def getUsers(config: Config): Option[List[ConfigUser]] = config.as[Option[List[ConfigUser]]](usersConfigurationPath)
  def getRules(usersFile: URI): List[ConfigRule] =
    ConfigFactoryExt.parseUri(usersFile, getClass.getClassLoader).as[List[ConfigRule]](rulesConfigurationPath)
  def getRules(config: Config): List[ConfigRule] = getRules(config.as[URI](usersConfigPath))

  final case class ConfigUser(
      identity: String,
      username: Option[String],
      password: Option[String],
      encryptedPassword: Option[String],
      roles: Set[String]
  )

  final case class ConfigRule(
      role: String,
      isAdmin: Boolean = false,
      categories: List[String] = List.empty,
      permissions: List[Permission] = List.empty,
      // Currently we don't use global permissions in our code, but it is possible to configure TopTab.requiredPermission
      // which can hide a tab on FE side when smb doesn't have some specific global permission. It is used in external project
      globalPermissions: List[GlobalPermission] = List.empty
  )

}
