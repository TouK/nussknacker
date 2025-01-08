package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

// TODO: We should extract a class for all configuration options that should be available to designer instead of returning raw hocon config.
//       Thanks to that it will be easier to split processing type config from rest of configs and use this interface programmatically
final case class DesignerConfig private (rawConfig: ConfigWithUnresolvedVersion) {

  import DesignerConfig._
  import net.ceedubs.ficus.Ficus._

  def processingTypeConfigs: ProcessingTypeConfigs =
    ProcessingTypeConfigs(processingTypeConfigsRaw.asMap.mapValuesNow(ProcessingTypeConfig.read))

  def processingTypeConfigsRaw: ConfigWithUnresolvedVersion =
    rawConfig
      .getConfigOpt("scenarioTypes")
      .getOrElse {
        throw ConfigurationMalformedException("No scenario types configuration provided")
      }

  def configLoaderConfig: Config = rawConfig.resolved.getAs[Config]("configLoader").getOrElse(ConfigFactory.empty())

  def managersDirs(): List[Path] = {
    val managersPath = "managersDirs"
    if (rawConfig.resolved.hasPath(managersPath)) {
      val managersDirs = rawConfig.resolved.getStringList(managersPath).asScala.toList
      val paths        = managersDirs.map(_.convertToURL().toURI).map(Paths.get)
      val invalidPaths = paths
        .map(p => (p, !Files.isDirectory(p)))
        .collect { case (p, true) => p }

      if (invalidPaths.isEmpty)
        paths
      else
        throw ConfigurationMalformedException(
          s"Cannot find the following directories: ${invalidPaths.mkString(", ")}"
        )
    } else {
      throw ConfigurationMalformedException(s"No '$managersPath' configuration path found")
    }
  }

}

object DesignerConfig {

  def from(config: Config): DesignerConfig = {
    DesignerConfig(ConfigWithUnresolvedVersion(config))
  }

  final case class ConfigurationMalformedException(msg: String) extends RuntimeException(msg)

}
