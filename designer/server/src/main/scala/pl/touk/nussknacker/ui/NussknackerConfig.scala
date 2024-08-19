package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.IO
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.ui.config.DesignerConfigLoader

import scala.jdk.CollectionConverters._

// todo: move
trait NussknackerConfig {

  def applicationConfig(): IO[ConfigWithUnresolvedVersion]

  final def processingTypeConfigs(): IO[Map[String, ConfigWithUnresolvedVersion]] = {
    applicationConfig()
      .map { config =>
        read(config, "scenarioTypes").getOrElse {
          throw new RuntimeException("No scenario types configuration provided")
        }
      }
    //    val config = loadConfig.value
    //    lastLoadedConfig = config // todo: continue

  }

  private def read(
      config: ConfigWithUnresolvedVersion,
      path: String
  ): Option[Map[String, ConfigWithUnresolvedVersion]] = {
    if (config.resolved.hasPath(path)) {
      val nestedConfig = config.getConfig(path)
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

class LoadableConfigBasedNussknackerConfig(loadConfig: IO[ConfigWithUnresolvedVersion]) extends NussknackerConfig {

  // todo:
  //  private var lastLoadedConfig = loadConfig.value

  override def applicationConfig(): IO[ConfigWithUnresolvedVersion] = loadConfig

}

class LoadableDesignerConfigBasedNussknackerConfig(classLoader: ClassLoader)
    extends LoadableConfigBasedNussknackerConfig(DesignerConfigLoader.load(classLoader))
