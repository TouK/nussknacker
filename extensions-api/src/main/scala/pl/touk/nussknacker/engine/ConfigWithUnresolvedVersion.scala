package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}

case class ConfigWithUnresolvedVersion private(withUnresolvedEnvVariables: Config, resolved: Config) {

  def getConfig(path: String): ConfigWithUnresolvedVersion = {
    ConfigWithUnresolvedVersion(withUnresolvedEnvVariables.getConfig(path), resolved.getConfig(path))
  }

}

object ConfigWithUnresolvedVersion {

  def apply(config: Config): ConfigWithUnresolvedVersion = ConfigWithUnresolvedVersion(getClass.getClassLoader, config)

  def apply(loader: ClassLoader, unresolvedConfig: Config): ConfigWithUnresolvedVersion = {
    val withUnresolvedEnvVariables = unresolvedConfig.resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true))
    val resolved = ConfigFactory.load(loader, unresolvedConfig)
    new ConfigWithUnresolvedVersion(withUnresolvedEnvVariables, resolved)
  }

}