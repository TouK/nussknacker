package pl.touk.nussknacker.ui.config

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigRenderOptions}
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.util.config.FicusReaders
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNotesSettings
import pl.touk.nussknacker.ui.config.DesignerConfig.{ConfigurationMalformedException, HttpConfig}
import pl.touk.nussknacker.ui.config.Implicits.ConfigExt
import pl.touk.nussknacker.ui.config.scenariotoolbar.{
  CategoriesScenarioToolbarsConfig,
  CategoriesScenarioToolbarsConfigParser
}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbConfig
import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig
import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig.MaxActiveScenariosCount
import pl.touk.nussknacker.ui.notifications.NotificationConfig
import pl.touk.nussknacker.ui.process.deployment.reconciliation.FinishedDeploymentsStatusesSynchronizationConfig
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig
import pl.touk.nussknacker.ui.process.newdeployment.synchronize.DeploymentsStatusesSynchronizationConfig
import pl.touk.nussknacker.ui.security.ssl.{KeyStoreConfig, SslConfigParser}

import java.nio.file.{Path, Paths}
import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

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
    val stickyNotesSettings: StickyNotesSettings,
    val deploymentsStatusesSynchronizationConfig: DeploymentsStatusesSynchronizationConfig,
    val finishedDeploymentStatusesSynchronization: FinishedDeploymentsStatusesSynchronizationConfig,
    val componentLinks: List[ComponentLinkConfig],
    val processToolbarConfig: CategoriesScenarioToolbarsConfig,
    val questDbSettings: QuestDbConfig,
    val notifications: NotificationConfig,
    val fragmentPropertiesDocsUrl: Option[String],
    val repositoryGaugesCacheDuration: Duration,
    val globalBuildInfo: Option[Map[String, String]],
    val ssl: Option[KeyStoreConfig],
    val http: HttpConfig,
    val attachments: AttachmentsConfig,
    val assistantSettings: AssistantSettings,
    val globalLimitsConfig: GlobalLimitsConfig,
    val testCasesSettings: TestCasesSettings,
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

  def render(): String = rawConfig.root().render(ConfigRenderOptions.concise())

}

object DesignerConfig {

  private[config] val defaultConfigResource = "defaultDesignerConfig.conf"

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def from(config: Config): DesignerConfig = {
    val defaultConfig = ConfigFactory.parseResources(getClass.getClassLoader, DesignerConfig.defaultConfigResource)
    val configWithFallbackToDefault = config.withFallback(defaultConfig)
    DesignerConfig.from(ConfigWithUnresolvedVersion(configWithFallbackToDefault))
  }

  def from(rawConfig: ConfigWithUnresolvedVersion): DesignerConfig = {
    val resolvedConfig               = rawConfig.resolved
    val managersDir                  = parseManagersDirs(resolvedConfig)
    val configLoaderConfig           = resolvedConfig.getAs[Config]("configLoader").getOrElse(ConfigFactory.empty())
    val environment                  = resolvedConfig.getString("environment")
    val usageStatisticsReportsConfig = resolvedConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")

    val environmentAlert  = resolvedConfig.getAsWithLogging[EnvironmentAlert]("environmentAlert")
    val isDevelopmentMode = resolvedConfig.hasPath("developmentMode") && resolvedConfig.getBoolean("developmentMode")
    val enableConfigEndpoint =
      resolvedConfig.hasPath("enableConfigEndpoint") && resolvedConfig.getBoolean("enableConfigEndpoint")
    val metrics = resolvedConfig
      .getAsWithLogging[MetricsSettings]("metricsSettings")
      .orElse(resolvedConfig.getAsWithLogging[MetricsSettings]("grafanaSettings"))
    val counts = resolvedConfig.getAsWithLogging[Config]("countsSettings")

    val remoteEnvironment         = resolvedConfig.getAsWithLogging[HttpRemoteEnvironmentConfig]("secondaryEnvironment")
    val commentSettings           = resolvedConfig.getAsWithLogging[CommentSettings]("commentSettings")
    val deploymentCommentSettings = parseDeploymentCommentSettings(resolvedConfig)
    val scenarioLabelSettings     = ScenarioLabelConfig.create(resolvedConfig)
    val scenarioStateTimeout      = resolvedConfig.getAsWithLogging[FiniteDuration]("scenarioStateTimeout")
    val surveySettings            = resolvedConfig.getAsWithLogging[SurveySettings]("surveySettings")

    implicit val tabDecoder: ValueReader[TopTab] = FicusReaders.forDecoder
    val tabs                                     = resolvedConfig.getAsWithLogging[List[TopTab]]("tabs")
    val intervalTimeSettings                     = resolvedConfig.as[IntervalTimeSettings]("intervalTimeSettings")
    val testDataSettings                         = resolvedConfig.as[TestDataSettings]("testDataSettings")
    val stickyNotesSettings =
      resolvedConfig.getAs[StickyNotesSettings]("stickyNotesSettings").getOrElse(StickyNotesSettings.default)
    val redirectAfterArchive              = resolvedConfig.getAs[Boolean]("redirectAfterArchive").getOrElse(true)
    val componentDefinitionExtractionMode = parseComponentDefinitionExtractionMode(resolvedConfig)

    val deploymentsStatusesSynchronizationConfig = resolvedConfig
      .getAs[DeploymentsStatusesSynchronizationConfig]("deploymentStatusesSynchronization")
      .getOrElse(DeploymentsStatusesSynchronizationConfig())

    val finishedDeploymentStatusesSynchronization = resolvedConfig
      .getAs[FinishedDeploymentsStatusesSynchronizationConfig]("finishedDeploymentStatusesSynchronization")
      .getOrElse(FinishedDeploymentsStatusesSynchronizationConfig())

    val componentLinks = parseComponentLinksConfig(resolvedConfig)

    val processToolbarConfig = CategoriesScenarioToolbarsConfigParser.parse(resolvedConfig)

    val questDbSettings = QuestDbConfig.parse(resolvedConfig)

    val notifications = resolvedConfig.as[NotificationConfig]("notifications")

    val fragmentPropertiesDocsUrl     = resolvedConfig.getAs[String]("fragmentPropertiesDocsUrl")
    val repositoryGaugesCacheDuration = resolvedConfig.getDuration("repositoryGaugesCacheDuration")
    val globalBuildInfo               = resolvedConfig.getAs[Map[String, String]]("globalBuildInfo")
    val ssl                           = SslConfigParser.parseSslConfig(resolvedConfig)
    val http                          = resolvedConfig.as[HttpConfig]("http")
    val attachments                   = AttachmentsConfig.parse(resolvedConfig)
    val assistantSettings =
      resolvedConfig.getAs[AssistantSettings]("assistantSettings").getOrElse(AssistantSettings.disabled)
    val testCasesSettings =
      resolvedConfig.as[TestCasesSettings]("testCasesSettings")

    val limitsConfig = if (resolvedConfig.hasPath("globalLimits")) {
      Try(resolvedConfig.getConfig("globalLimits").getInt("maxActiveScenariosCount")) match {
        case Success(maxActiveScenariosCount) =>
          GlobalLimitsConfig(maxActiveScenariosCount = Some(MaxActiveScenariosCount(maxActiveScenariosCount)))
        case Failure(_: ConfigException.Missing) =>
          GlobalLimitsConfig(maxActiveScenariosCount = None)
        case Failure(exception) =>
          throw exception
      }
    } else {
      GlobalLimitsConfig.default
    }

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
      stickyNotesSettings = stickyNotesSettings,
      deploymentsStatusesSynchronizationConfig = deploymentsStatusesSynchronizationConfig,
      finishedDeploymentStatusesSynchronization = finishedDeploymentStatusesSynchronization,
      componentLinks = componentLinks,
      processToolbarConfig = processToolbarConfig,
      questDbSettings = questDbSettings,
      notifications = notifications,
      fragmentPropertiesDocsUrl = fragmentPropertiesDocsUrl,
      repositoryGaugesCacheDuration = repositoryGaugesCacheDuration,
      globalBuildInfo = globalBuildInfo,
      ssl = ssl,
      http = http,
      attachments = attachments,
      assistantSettings = assistantSettings,
      globalLimitsConfig = limitsConfig,
      testCasesSettings = testCasesSettings,
    )
  }

  private[ui] def parseComponentLinksConfig(resolvedConfig: Config) = {
    implicit val optionListReader: ValueReader[List[ComponentLinkConfig]] = (config: Config, path: String) =>
      ValueReader[List[Config]]
        .read(config, path)
        .map(_.as[ComponentLinkConfig])

    resolvedConfig.getAs[List[ComponentLinkConfig]]("componentLinks").getOrElse(List.empty)
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

  final case class HttpConfig(interface: String, port: Int, publicPath: String)

  final case class ConfigurationMalformedException(msg: String) extends RuntimeException(msg)

}
