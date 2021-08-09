package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigResolveOptions}

/**
  * This class holds both resolved (loaded) config and unresolved version. It is useful in case when you want to
  * defer resolution of environment variables used in config to moment when they will be available in correct form.
  */
case class LoadedConfig(loadedConfig: Config, unresolvedConfig: UnresolvedConfig) {

  import scala.collection.JavaConverters._

  def get(path: String): LoadedConfig = {
    LoadedConfig(loadedConfig.getConfig(path), unresolvedConfig.map(_.getConfig(path)))
  }

  def getOpt(path: String): Option[LoadedConfig] = {
    if (loadedConfig.hasPath(path)) {
      Some(LoadedConfig(loadedConfig.getConfig(path), unresolvedConfig.map(_.getConfig(path))))
    } else {
      None
    }
  }

  def entries: Map[String, LoadedConfig] = {
    loadedConfig.root().asScala.map {
      case (key, _) =>
        key -> LoadedConfig(loadedConfig.getConfig(key), unresolvedConfig.map(_.getConfig(key)))
    }.toMap
  }

}

case class UnresolvedConfig(config: Config, resolutionSource: Config) {

  def map(f: Config => Config): UnresolvedConfig = copy(f(config))

  def resolve(options: ConfigResolveOptions): Config =
    config.resolveWith(resolutionSource, options)

}

object LoadedConfig {

  def load(unresolvedConfig: Config): LoadedConfig =
    LoadedConfig(unresolvedConfig.resolve(), unresolvedConfig)

  def apply(loadedConfig: Config, unresolvedConfig: Config): LoadedConfig =
    LoadedConfig(loadedConfig, UnresolvedConfig(unresolvedConfig, loadedConfig))

}
