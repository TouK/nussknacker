package pl.touk.nussknacker.ui

import cats.effect.IO
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.util.config.ConfigWithUnresolvedVersionExt._
import pl.touk.nussknacker.ui.config.DesignerConfigLoader

trait NussknackerConfig {

  def loadApplicationConfig(): IO[ConfigWithUnresolvedVersion]

  final def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]] = {
    loadApplicationConfig()
      .map { config =>
        config
          .readMap("scenarioTypes")
          .getOrElse { throw new RuntimeException("No scenario types configuration provided") }
          .mapValuesNow(ProcessingTypeConfig.read)
      }
  }

}

class LoadableConfigBasedNussknackerConfig(loadConfig: IO[ConfigWithUnresolvedVersion]) extends NussknackerConfig {

  override def loadApplicationConfig(): IO[ConfigWithUnresolvedVersion] = loadConfig

}

class LoadableDesignerConfigBasedNussknackerConfig(classLoader: ClassLoader)
    extends LoadableConfigBasedNussknackerConfig(DesignerConfigLoader.load(classLoader))
