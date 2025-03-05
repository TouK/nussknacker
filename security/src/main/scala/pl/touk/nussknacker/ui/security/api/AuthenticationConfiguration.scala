package pl.touk.nussknacker.ui.security.api

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.{getRules, usersConfigurationPath, ConfigUser}

import java.net.URI

import GlobalPermission.GlobalPermission

trait AuthenticationConfiguration {
  def name: String
  def usersFile: URI

  def anonymousUserRole: Option[String]

  def isAdminImpersonationPossible: Boolean

  private lazy val userConfig: Config = new ConfigFactoryExt(getClass.getClassLoader).parseUri(usersFile)

  protected lazy val usersOpt: Option[List[ConfigUser]] =
    userConfig.as[Option[List[ConfigUser]]](usersConfigurationPath)

  lazy val users: List[ConfigUser] = usersOpt
    .getOrElse(
      throw new IllegalArgumentException(
        s"Missing field ${AuthenticationConfiguration.usersConfigurationPath} at ${userConfig.getConfig(AuthenticationConfiguration.usersConfigPath)} users config file."
      )
    )

  lazy val rules: List[AuthenticationConfiguration.ConfigRule] = getRules(usersFile)

}

object AuthenticationConfiguration {

  val authenticationConfigPath = "authentication"
  val methodConfigPath         = s"$authenticationConfigPath.method"
  val usersConfigPath          = s"$authenticationConfigPath.usersFile"
  val usersConfigurationPath   = "users"
  val rulesConfigurationPath   = "rules"

  private[security] def getRules(usersFile: URI): List[ConfigRule] =
    new ConfigFactoryExt(getClass.getClassLoader).parseUri(usersFile).as[List[ConfigRule]](rulesConfigurationPath)

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
