package pl.touk.nussknacker.ui.security.basicauth

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.cache.CacheConfig
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration

import java.net.URI
import scala.concurrent.duration._

case class BasicAuthenticationConfiguration(
    usersFile: URI,
    cachingHashes: Option[CachingHashesConfig],
    anonymousUserRole: Option[String] = None,
    isAdminImpersonationPossible: Boolean = false,
    realm: Option[String] = None
) extends AuthenticationConfiguration {
  override def name: String = BasicAuthenticationConfiguration.name

  def cachingHashesOrDefault: CachingHashesConfig = cachingHashes.getOrElse(CachingHashesConfig.defaultConfig)

  def implicitGrantEnabled: Boolean = false

  def idTokenNonceVerificationRequired: Boolean = false
}

object BasicAuthenticationConfiguration {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  import AuthenticationConfiguration._

  val name: String = "BasicAuth"

  def create(config: Config): BasicAuthenticationConfiguration =
    config.as[BasicAuthenticationConfiguration](authenticationConfigPath)
}

final case class CachingHashesConfig(
    enabled: Option[Boolean],
    maximumSize: Option[Long],
    expireAfterAccess: Option[FiniteDuration],
    expireAfterWrite: Option[FiniteDuration]
) {

  def isEnabled: Boolean = enabled.getOrElse(CachingHashesConfig.defaultEnabledValue)

  def toCacheConfig: Option[CacheConfig[(String, String), String]] =
    if (isEnabled) {
      Some(
        CacheConfig(
          maximumSize.getOrElse(CacheConfig.defaultMaximumSize),
          expireAfterAccess.orElse(CachingHashesConfig.defaultExpireAfterAccess),
          expireAfterWrite.orElse(CachingHashesConfig.defaultExpireAfterWrite)
        )
      )
    } else {
      None
    }

}

object CachingHashesConfig {

  val defaultEnabledValue: Boolean                     = false
  val defaultExpireAfterAccess: Option[FiniteDuration] = Some(1.hour)
  val defaultExpireAfterWrite: Option[FiniteDuration]  = None
  val defaultConfig: CachingHashesConfig = CachingHashesConfig(Some(defaultEnabledValue), None, None, None)

}
