package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.ui.config.DesignerConfig.ConfigurationMalformedException
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters._

final class DesignerConfig private (
    // unresolved version is only needed for deployment where we want to pass to engine unresolved version and resolve it on the engine side
    rawConfigWithUnresolvedVersion: ConfigWithUnresolvedVersion,
    val managersDir: List[Path],
    val configLoaderConfig: Config,
    val environment: String,
    // TODO: inline all FeatureTogglesConfig as fields
    val featureTogglesConfig: FeatureTogglesConfig,
    val usageStatisticsReportsConfig: UsageStatisticsReportsConfig
) {

  // TODO: We should parse configuration options to fields instead of accessing rawConfig. Thank to that:
  //       - we will get errors faster if there is some problem in configuration
  //       - structure of the config will be visible in classes
  val rawConfig: Config = rawConfigWithUnresolvedVersion.resolved

  def processingTypeConfigs(): ProcessingTypeConfigs =
    ProcessingTypeConfigs(processingTypeConfigsRaw().asMap.mapValuesNow(ProcessingTypeConfig.read))

  def processingTypeConfigsRaw(): ConfigWithUnresolvedVersion =
    rawConfigWithUnresolvedVersion
      .getConfigOpt("scenarioTypes")
      .getOrElse {
        throw ConfigurationMalformedException("No scenario types configuration provided")
      }

}

object DesignerConfig {

  private[config] val defaultConfigResource = "defaultDesignerConfig.conf"

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader

  def from(config: Config): DesignerConfig = {
    val defaultConfig = ConfigFactory.parseResources(getClass.getClassLoader, DesignerConfig.defaultConfigResource)
    val configWithFallbackToDefault = config.withFallback(defaultConfig)
    DesignerConfig(ConfigWithUnresolvedVersion(configWithFallbackToDefault))
  }

  def apply(rawConfig: ConfigWithUnresolvedVersion): DesignerConfig = {
    val resolvedConfig               = rawConfig.resolved
    val managersDir                  = parseManagersDirs(resolvedConfig)
    val configLoaderConfig           = resolvedConfig.getAs[Config]("configLoader").getOrElse(ConfigFactory.empty())
    val environment                  = resolvedConfig.getString("environment")
    val featureTogglesConfig         = FeatureTogglesConfig.create(resolvedConfig)
    val usageStatisticsReportsConfig = resolvedConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")
    new DesignerConfig(
      rawConfig,
      managersDir,
      configLoaderConfig,
      environment,
      featureTogglesConfig,
      usageStatisticsReportsConfig
    )
  }

  private def parseManagersDirs(rawConfig: Config): List[Path] = {
    val managersPath = "managersDirs"
    if (rawConfig.hasPath(managersPath)) {
      val managersDirs = rawConfig.getStringList(managersPath).asScala.toList
      managersDirs.map(_.convertToURL().toURI).map(Paths.get)
    } else {
      throw ConfigurationMalformedException(s"No '$managersPath' configuration path found")
    }
  }

  final case class ConfigurationMalformedException(msg: String) extends RuntimeException(msg)
}
