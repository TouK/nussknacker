package pl.touk.nussknacker.ui

import cats.effect.IO
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.util.config.ConfigWithUnresolvedVersionExt._
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.config.DesignerConfigLoader

import java.nio.file.{Files, Path, Paths}

trait NussknackerConfig {

  def loadApplicationConfig(): IO[ConfigWithUnresolvedVersion]

  final def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]] = {
    loadApplicationConfig()
      .map { config =>
        config
          .readMap("scenarioTypes")
          .getOrElse { throw ConfigurationMalformedException("No scenario types configuration provided") }
          .mapValuesNow(ProcessingTypeConfig.read)
      }
  }

  final def managersDir(): IO[Path] = {
    loadApplicationConfig()
      .map { config =>
        config.readSafeString("managersDir") match {
          case Some(managersDirStr) =>
            val managersDir = Paths.get(managersDirStr.convertToURL().toURI)
            if (Files.isDirectory(managersDir)) managersDir
            else throw ConfigurationMalformedException(s"No '$managersDirStr' directory found")
          case None =>
            throw ConfigurationMalformedException(s"No 'managersDir' configuration path found")
        }
      }
  }

}

final case class ConfigurationMalformedException(msg: String) extends RuntimeException(msg)

class LoadableConfigBasedNussknackerConfig(loadConfig: IO[ConfigWithUnresolvedVersion]) extends NussknackerConfig {

  override def loadApplicationConfig(): IO[ConfigWithUnresolvedVersion] = loadConfig
}

class LoadableDesignerConfigBasedNussknackerConfig(classLoader: ClassLoader)
    extends LoadableConfigBasedNussknackerConfig(DesignerConfigLoader.load(classLoader))
