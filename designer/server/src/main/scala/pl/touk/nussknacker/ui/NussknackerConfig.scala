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

  final def managersDirs(): IO[List[Path]] = {
    loadApplicationConfig()
      .map { config =>
        config.readStringList("managersDirs") match {
          case Some(managersDirs) =>
            val paths = managersDirs.map(_.convertToURL().toURI).map(Paths.get)
            val invalidPaths = paths
              .map(p => (p, !Files.isDirectory(p)))
              .collect { case (p, true) => p }

            if (invalidPaths.isEmpty) paths
            else
              throw ConfigurationMalformedException(
                s"Cannot find the following directories: ${invalidPaths.mkString(", ")}"
              )
          case None =>
            throw ConfigurationMalformedException(s"No 'managersDirs' configuration path found")
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
