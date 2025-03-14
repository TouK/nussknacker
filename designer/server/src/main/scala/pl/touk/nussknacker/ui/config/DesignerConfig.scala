package pl.touk.nussknacker.ui.config

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.util.config.FicusReaders
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesSettings
import pl.touk.nussknacker.ui.config.DesignerConfig.ConfigurationMalformedException
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig

import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

final class DesignerConfig private (
    // unresolved version is only needed for deployment where we want to pass to engine unresolved version and resolve it on the engine side
    rawConfigWithUnresolvedVersion: ConfigWithUnresolvedVersion,
    val managersDir: List[Path],
    val configLoaderConfig: Config,
    val environment: String,
    val usageStatisticsReportsConfig: UsageStatisticsReportsConfig,
    val development: Boolean,
    val metrics: Option[MetricsSettings],
    val remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
    val counts: Option[Config],
    val environmentAlert: Option[EnvironmentAlert],
    val commentSettings: Option[CommentSettings],
    val deploymentCommentSettings: Option[DeploymentCommentSettings],
    val scenarioLabelConfig: Option[ScenarioLabelConfig],
    val scenarioStateTimeout: Option[FiniteDuration],
    val surveySettings: Option[SurveySettings],
    val tabs: Option[List[TopTab]],
    val intervalTimeSettings: IntervalTimeSettings,
    val testDataSettings: TestDataSettings,
    val enableConfigEndpoint: Boolean,
    val redirectAfterArchive: Boolean,
    val componentDefinitionExtractionMode: ComponentDefinitionExtractionMode,
    val stickyNotesSettings: StickyNotesSettings
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
    val usageStatisticsReportsConfig = resolvedConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")

    val environmentAlert  = parseOptionalConfig[EnvironmentAlert](resolvedConfig, "environmentAlert")
    val isDevelopmentMode = resolvedConfig.hasPath("developmentMode") && resolvedConfig.getBoolean("developmentMode")
    val enableConfigEndpoint =
      resolvedConfig.hasPath("enableConfigEndpoint") && resolvedConfig.getBoolean("enableConfigEndpoint")
    val metrics = parseOptionalConfig[MetricsSettings](resolvedConfig, "metricsSettings")
      .orElse(parseOptionalConfig[MetricsSettings](resolvedConfig, "grafanaSettings"))
    val counts = parseOptionalConfig[Config](resolvedConfig, "countsSettings")

    val remoteEnvironment = parseOptionalConfig[HttpRemoteEnvironmentConfig](resolvedConfig, "secondaryEnvironment")
    val commentSettings   = parseOptionalConfig[CommentSettings](resolvedConfig, "commentSettings")
    val deploymentCommentSettings = parseDeploymentCommentSettings(resolvedConfig)
    val scenarioLabelSettings     = ScenarioLabelConfig.create(resolvedConfig)
    val scenarioStateTimeout      = parseOptionalConfig[FiniteDuration](resolvedConfig, "scenarioStateTimeout")
    val surveySettings            = parseOptionalConfig[SurveySettings](resolvedConfig, "surveySettings")

    implicit val tabDecoder: ValueReader[TopTab] = FicusReaders.forDecoder
    val tabs                                     = parseOptionalConfig[List[TopTab]](resolvedConfig, "tabs")
    val intervalTimeSettings                     = resolvedConfig.as[IntervalTimeSettings]("intervalTimeSettings")
    val testDataSettings                         = resolvedConfig.as[TestDataSettings]("testDataSettings")
    val stickyNotesSettings =
      resolvedConfig.getAs[StickyNotesSettings]("stickyNotesSettings").getOrElse(StickyNotesSettings.default)
    val redirectAfterArchive              = resolvedConfig.getAs[Boolean]("redirectAfterArchive").getOrElse(true)
    val componentDefinitionExtractionMode = parseComponentDefinitionExtractionMode(resolvedConfig)

    new DesignerConfig(
      rawConfigWithUnresolvedVersion = rawConfig,
      managersDir = managersDir,
      configLoaderConfig = configLoaderConfig,
      environment = environment,
      usageStatisticsReportsConfig = usageStatisticsReportsConfig,
      development = isDevelopmentMode,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      commentSettings = commentSettings,
      deploymentCommentSettings = deploymentCommentSettings,
      scenarioLabelConfig = scenarioLabelSettings,
      scenarioStateTimeout = scenarioStateTimeout,
      surveySettings = surveySettings,
      tabs = tabs,
      intervalTimeSettings = intervalTimeSettings,
      environmentAlert = environmentAlert,
      testDataSettings = testDataSettings,
      enableConfigEndpoint = enableConfigEndpoint,
      redirectAfterArchive = redirectAfterArchive,
      componentDefinitionExtractionMode = componentDefinitionExtractionMode,
      stickyNotesSettings = stickyNotesSettings
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

  private def parseDeploymentCommentSettings(config: Config): Option[DeploymentCommentSettings] = {
    val rootPath = "deploymentCommentSettings"
    if (config.hasPath(rootPath)) {
      val settingConfig     = config.getConfig(rootPath)
      val validationPattern = settingConfig.as[String](s"validationPattern")
      val exampleComment    = settingConfig.getAs[String](s"exampleComment")
      DeploymentCommentSettings.create(validationPattern, exampleComment) match {
        case Valid(settings) => Some(settings)
        case Invalid(e)      => throw e
      }
    } else {
      None
    }
  }

  private def parseComponentDefinitionExtractionMode(config: Config): ComponentDefinitionExtractionMode = {
    val configPath = "enableBasicDefinitionsForComponents"
    if (config.hasPath(configPath) && config.getBoolean(configPath)) {
      ComponentDefinitionExtractionMode.FinalAndBasicDefinitions
    } else {
      ComponentDefinitionExtractionMode.FinalDefinition
    }
  }

  final case class ConfigurationMalformedException(msg: String) extends RuntimeException(msg)

}
