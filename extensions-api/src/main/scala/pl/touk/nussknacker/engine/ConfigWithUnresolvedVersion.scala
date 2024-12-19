package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}

import scala.jdk.CollectionConverters._

case class ConfigWithUnresolvedVersion private (withUnresolvedEnvVariables: Config, resolved: Config) {

  def getConfig(path: String): ConfigWithUnresolvedVersion = {
    ConfigWithUnresolvedVersion(withUnresolvedEnvVariables.getConfig(path), resolved.getConfig(path))
  }

  def getConfigOpt(path: String): Option[ConfigWithUnresolvedVersion] = {
    if (resolved.hasPath(path))
      Some(ConfigWithUnresolvedVersion(withUnresolvedEnvVariables.getConfig(path), resolved.getConfig(path)))
    else
      None
  }

  def asMap: Map[String, ConfigWithUnresolvedVersion] = {
    resolved
      .root()
      .entrySet()
      .asScala
      .map(_.getKey)
      .map { key => key -> getConfig(key) }
      .toMap
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
