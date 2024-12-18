package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}

import scala.jdk.CollectionConverters._

case class ConfigWithUnresolvedVersion private (withUnresolvedEnvVariables: Config, resolved: Config) {

  def getConfig(path: String): ConfigWithUnresolvedVersion = {
    ConfigWithUnresolvedVersion(withUnresolvedEnvVariables.getConfig(path), resolved.getConfig(path))
  }

  def readMap(path: String): Option[Map[String, ConfigWithUnresolvedVersion]] = {
    if (resolved.hasPath(path)) {
      val nestedConfig = getConfig(path)
      Some(
        nestedConfig.resolved
          .root()
          .entrySet()
          .asScala
          .map(_.getKey)
          .map { key => key -> nestedConfig.getConfig(key) }
          .toMap
      )
    } else {
      None
    }
  }

}

object ConfigWithUnresolvedVersion {

  def apply(config: Config): ConfigWithUnresolvedVersion = ConfigWithUnresolvedVersion(getClass.getClassLoader, config)

  def apply(loader: ClassLoader, unresolvedConfig: Config): ConfigWithUnresolvedVersion = {
    val withUnresolvedEnvVariables = unresolvedConfig.resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true))
    val resolved                   = ConfigFactory.load(loader, unresolvedConfig)
    new ConfigWithUnresolvedVersion(withUnresolvedEnvVariables, resolved)
  }

}
