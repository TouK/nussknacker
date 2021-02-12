package pl.touk.nussknacker.ui.security.api

import java.io.File
import java.net.URI
import java.security.PublicKey

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.cache.CacheConfig
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.{ConfigRule, ConfigUser}
import pl.touk.nussknacker.ui.security.api.AuthenticationMethod.AuthenticationMethod
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

import scala.concurrent.duration._

trait AuthenticationConfiguration {
  def authorizeUrl: Option[URI] = Option.empty
  def authSeverPublicKey: Option[PublicKey] = Option.empty
  def idTokenNonceVerificationRequired: Boolean
  def implicitGrantEnabled: Boolean
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

case class DefaultAuthenticationConfiguration(method: AuthenticationMethod = AuthenticationMethod.Other, usersFile: String,
                                              cachingHashes: Option[CachingHashesConfig]) extends AuthenticationConfiguration {

  def cachingHashesOrDefault: CachingHashesConfig = cachingHashes.getOrElse(CachingHashesConfig.defaultConfig)

  def implicitGrantEnabled: Boolean = false

  def idTokenNonceVerificationRequired: Boolean = false
}

object DefaultAuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import AuthenticationConfiguration._

  def create(config: Config): DefaultAuthenticationConfiguration =
    config.as[DefaultAuthenticationConfiguration](authenticationConfigPath)
}

case class CachingHashesConfig(enabled: Option[Boolean],
                               maximumSize: Option[Long],
                               expireAfterAccess: Option[FiniteDuration],
                               expireAfterWrite: Option[FiniteDuration]) {

  def isEnabled: Boolean = enabled.getOrElse(CachingHashesConfig.defaultEnabledValue)

  def toCacheConfig: Option[CacheConfig[(String, String), String]] =
    if (isEnabled) {
      Some(CacheConfig(
        maximumSize.getOrElse(CacheConfig.defaultMaximumSize),
        expireAfterAccess.orElse(CachingHashesConfig.defaultExpireAfterAccess),
        expireAfterWrite.orElse(CachingHashesConfig.defaultExpireAfterWrite)
      ))
    } else {
      None
    }

}

object CachingHashesConfig {

  val defaultEnabledValue: Boolean = false
  val defaultExpireAfterAccess: Option[FiniteDuration] = Some(1.hour)
  val defaultExpireAfterWrite: Option[FiniteDuration] = None
  val defaultConfig: CachingHashesConfig = CachingHashesConfig(Some(defaultEnabledValue), None, None, None)

}