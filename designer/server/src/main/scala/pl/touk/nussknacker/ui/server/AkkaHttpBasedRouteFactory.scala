package pl.touk.nussknacker.ui.server

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.processCounts.CountsReporter
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.{AttachmentsConfig, DesignerConfig, UsageStatisticsReportsConfig}
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.customhttpservice.CustomHttpServiceProvider
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.migrations.{MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.notifications.{Notification, NotificationConfig, NotificationServiceImpl}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.{
  ActionService,
  DeploymentManagerDispatcher,
  DeploymentService => LegacyDeploymentService
}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.label.ScenarioLabelsService
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.process.newactivity.ActivityService
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentRepository, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtype.{CombinedProcessingTypeData, ProcessingTypeServices}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.stickynotes.DbStickyNotesRepository
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.process.version.{ScenarioGraphVersionRepository, ScenarioGraphVersionService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, AuthManager, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.statistics.{
  FingerprintService,
  PublicEncryptionKey,
  StatisticUrlConfig,
  UsageStatisticsReportsSettingsService
}
import pl.touk.nussknacker.ui.util._
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator

import java.time.Clock
import scala.concurrent.Future

class AkkaHttpBasedRouteFactory(
    clock: Clock,
    dbRef: DbRef,
    dbioRunner: DBIOActionRunner,
    metricsRegistry: MetricRegistry,
    designerConfig: DesignerConfig,
    statisticsPublicKey: String,
    futureProcessRepository: FetchingProcessRepository[Future],
    scenarioActivityRepository: ScenarioActivityRepository,
    scenarioLabelsRepository: ScenarioLabelsRepository,
    globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
    feStatisticsRepository: FEStatisticsRepository[Future],
    componentService: ComponentService,
    processService: ProcessService,
    fetchScenarioActivityService: FetchScenarioActivityService,
    actionRepository: ScenarioActionRepository,
    deploymentRepository: DeploymentRepository,
    processChangeListener: ProcessChangeListener,
    actionService: ActionService,
    countsReporter: Option[CountsReporter[Future]],
    counter: ProcessCounter,
    fingerprintService: FingerprintService,
    scenarioStatusProvider: ScenarioStatusProvider,
    scenarioStatusPresenter: ScenarioStatusPresenter,
    dmDispatcher: DeploymentManagerDispatcher,
    processingTypeServicesProvider: ProcessingTypeDataProvider[ProcessingTypeServices, CombinedProcessingTypeData],
    reloadProcessingTypes: IO[Unit],
    processAuthorizer: AuthorizeProcess,
    authenticationResources: AuthenticationResources,
    authManager: AuthManager,
    customHttpServiceProviders: Map[String, CustomHttpServiceProvider],
)(
    implicit system: ActorSystem,
    executionContextWithIORuntime: ExecutionContextWithIORuntime,
) extends LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def createRoute: Route = {
    val usageStatisticsReportsConfig =
      designerConfig.rawConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")

    val apiResourcesWithAuthentication: List[RouteWithUser] = {
      val processResources = {
        val configProcessToolbarService = new ConfigScenarioToolbarService(
          CategoriesScenarioToolbarsConfigParser.parse(designerConfig.rawConfig)
        )
        new ProcessesResources(
          processService = processService,
          scenarioStatusProvider = scenarioStatusProvider,
          scenarioStatusPresenter = scenarioStatusPresenter,
          processToolbarService = configProcessToolbarService,
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener
        )
      }
      val processExportResources = new ProcessesExportResources(
        futureProcessRepository,
        processService,
        scenarioActivityRepository,
        processingTypeServicesProvider.mapValues(_.processResolver),
        dbioRunner,
      )
      val managementResources = {
        val legacyDeploymentService = new LegacyDeploymentService(
          dmDispatcher,
          processingTypeServicesProvider.mapValues(_.scenarioValidator),
          processingTypeServicesProvider.mapValues(_.scenarioResolver),
          actionService,
          processingTypeServicesProvider.mapValues(_.additionalComponentConfigs)
        )
        new ManagementResources(
          processAuthorizer,
          processService,
          legacyDeploymentService,
          dmDispatcher,
          metricsRegistry,
          processingTypeServicesProvider.mapValues(_.scenarioTestService),
        )
      }
      val validationResource =
        new ValidationResources(processService, processingTypeServicesProvider.mapValues(_.processResolver))
      val definitionResources = new DefinitionResources(
        processingTypeServicesProvider.mapValues(_.definitionService)
      )
      val statusResources = {
        val stateDefinitionService = new ProcessStateDefinitionService(
          processingTypeServicesProvider
            .mapValues(_.category)
            .mapCombined(_.statusNameToStateDefinitionsMapping)
        )
        new StatusResources(stateDefinitionService)
      }
      val routes = List(
        processResources,
        processExportResources,
        managementResources,
        validationResource,
        definitionResources,
        statusResources,
      )

      val optionalRoutes = List(
        designerConfig.featureTogglesConfig.remoteEnvironment
          .map { migrationConfig =>
            val remoteEnvironment = new HttpRemoteEnvironment(
              migrationConfig,
              new TestModelMigrations(
                processingTypeServicesProvider
                  .mapValues(_.designerModelData.modelData.migrations)
                  .mapValues(new ProcessModelMigrator(_)),
                processingTypeServicesProvider.mapValues(_.scenarioValidator)
              ),
              designerConfig.environment
            )
            new RemoteEnvironmentResources(
              remoteEnvironment,
              processService,
              processAuthorizer,
              scenarioActivityRepository,
              dbioRunner,
              clock,
            )
          },
        countsReporter.map { reporter =>
          new ProcessReportResources(reporter, counter, futureProcessRepository, processService)
        },
      ).flatten

      val customHttpServiceRoutes = customHttpServiceProviders.map { case (name, provider) =>
        new RouteWithUser {
          override protected def securedRoute(implicit user: LoggedUser): Route =
            pathPrefix("custom" / name) {
              provider.provideRouteWithUser(user)
            }
        }
      }

      routes ++ optionalRoutes ++ customHttpServiceRoutes
    }

    val apiResourcesWithoutAuthentication: List[Route] = {
      // TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
      val settingsResources = new SettingsResources(
        designerConfig.featureTogglesConfig,
        authenticationResources.name,
        usageStatisticsReportsConfig,
        fingerprintService
      )
      List(
        settingsResources.publicRoute(),
        authenticationResources.routeWithPathPrefix,
      )
    }

    val nuDesignerApi = {
      val appApiHttpService = new AppApiHttpService(
        designerConfig = designerConfig,
        authManager = authManager,
        reloadProcessingTypes = reloadProcessingTypes,
        modelInfos = processingTypeServicesProvider.mapValues(_.designerModelData.modelData.info),
        categories = processingTypeServicesProvider.mapValues(_.category),
        processService = processService,
        shouldExposeConfig = designerConfig.featureTogglesConfig.enableConfigEndpoint,
      )

      val migrationApiHttpService = {
        val migrationApiAdapterService = new MigrationApiAdapterService()

        val migrationService = new MigrationService(
          designerConfig = designerConfig,
          processService = processService,
          processResolver = processingTypeServicesProvider.mapValues(_.processResolver),
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener,
          scenarioParametersService = processingTypeServicesProvider.mapCombined(_.parametersService),
          useLegacyCreateScenarioApi = true,
          migrationApiAdapterService = migrationApiAdapterService
        )

        new MigrationApiHttpService(
          authManager = authManager,
          migrationService = migrationService,
          migrationApiAdapterService = migrationApiAdapterService
        )
      }
      val componentsApiHttpService = new ComponentApiHttpService(
        authManager = authManager,
        componentService = componentService
      )
      val userApiHttpService = new UserApiHttpService(
        authManager = authManager,
        categories = processingTypeServicesProvider.mapValues(_.category)
      )

      val scenarioLabelsApiHttpService = new ScenarioLabelsApiHttpService(
        authManager = authManager,
        service = new ScenarioLabelsService(
          scenarioLabelsRepository,
          new ScenarioLabelsValidator(designerConfig.featureTogglesConfig.scenarioLabelConfig),
          dbioRunner
        )
      )

      val notificationApiHttpService = {
        val notificationsConfig = designerConfig.rawConfig.as[NotificationConfig]("notifications")
        val notificationService = new NotificationServiceImpl(
          fetchScenarioActivityService,
          actionRepository,
          globalNotificationRepository,
          dbioRunner,
          notificationsConfig
        )
        new NotificationApiHttpService(
          authManager = authManager,
          notificationService = notificationService
        )
      }

      val nodesApiHttpService = new NodesApiHttpService(
        authManager = authManager,
        processingTypeToConfig = processingTypeServicesProvider.mapValues(_.designerModelData.modelData),
        processingTypeToProcessValidator = processingTypeServicesProvider.mapValues(_.scenarioValidator),
        processingTypeToNodeValidator = processingTypeServicesProvider.mapValues(_.nodeValidator),
        processingTypeToExpressionSuggester = processingTypeServicesProvider.mapValues(_.expressionSuggester),
        processingTypeToParametersValidator = processingTypeServicesProvider.mapValues(_.parametersValidator),
        processingTypeToScenarioTestServices = processingTypeServicesProvider.mapValues(_.scenarioTestService),
        scenarioService = processService,
      )

      val testingApiHttpService = new TestingApiHttpService(
        authManager = authManager,
        processingTypeToParametersValidator = processingTypeServicesProvider.mapValues(_.parametersValidator),
        processingTypeToScenarioTestServices = processingTypeServicesProvider.mapValues(_.scenarioTestService),
        scenarioService = processService,
      )

      val actionInfoHttpService = new ActionInfoHttpService(
        authManager = authManager,
        processingTypeToActionInfoService = processingTypeServicesProvider.mapValues(_.actionInfoService),
        scenarioService = processService,
      )

      val stickyNotesApiHttpService = {
        val stickyNotesRepository = DbStickyNotesRepository.create(dbRef, clock)(executionContextWithIORuntime)
        new StickyNotesApiHttpService(
          authManager = authManager,
          stickyNotesRepository = stickyNotesRepository,
          scenarioService = processService,
          scenarioAuthorizer = processAuthorizer,
          dbioRunner,
          stickyNotesSettings = designerConfig.featureTogglesConfig.stickyNotesSettings
        )
      }

      val scenarioActivityApiHttpService = new ScenarioActivityApiHttpService(
        authManager = authManager,
        fetchScenarioActivityService = fetchScenarioActivityService,
        scenarioActivityRepository = scenarioActivityRepository,
        scenarioService = processService,
        scenarioAuthorizer = processAuthorizer,
        new ScenarioAttachmentService(
          AttachmentsConfig.create(designerConfig.rawConfig),
          scenarioActivityRepository,
          dbioRunner,
        ),
        designerConfig.featureTogglesConfig.deploymentCommentSettings,
        new AkkaHttpBasedTapirStreamEndpointProvider(),
        dbioRunner,
      )
      val scenarioParametersHttpService = new ScenarioParametersApiHttpService(
        authManager = authManager,
        scenarioParametersService = processingTypeServicesProvider.mapCombined(_.parametersService)
      )
      val dictApiHttpService = new DictApiHttpService(
        authManager = authManager,
        processingTypeData = processingTypeServicesProvider.mapValues { services =>
          (
            services.designerModelData.modelData.designerDictServices.dictQueryService,
            services.designerModelData.modelData.modelDefinition.expressionConfig.dictionaries,
            services.designerModelData.modelData.modelClassLoader
          )
        }
      )
      val deploymentHttpService = {
        val scenarioMetadataRepository     = new ScenarioMetadataRepository(dbRef)
        val scenarioGraphVersionRepository = new ScenarioGraphVersionRepository(dbRef)
        val scenarioGraphVersionService =
          new ScenarioGraphVersionService(
            scenarioGraphVersionRepository,
            processingTypeServicesProvider.mapValues(_.scenarioValidator),
            processingTypeServicesProvider.mapValues(_.scenarioResolver),
            dbioRunner
          )
        val deploymentService =
          new DeploymentService(
            scenarioMetadataRepository,
            scenarioGraphVersionService,
            deploymentRepository,
            dmDispatcher,
            dbioRunner,
            Clock.systemDefaultZone(),
            processingTypeServicesProvider.mapValues(_.additionalComponentConfigs)
          )
        val activityService =
          new ActivityService(
            designerConfig.featureTogglesConfig.deploymentCommentSettings,
            scenarioActivityRepository,
            deploymentService,
            dbioRunner,
            clock,
          )
        new DeploymentApiHttpService(authManager, activityService, deploymentService)
      }

      val statisticsApiHttpService = {
        val usageStatisticsReportsSettingsService = UsageStatisticsReportsSettingsService(
          usageStatisticsReportsConfig,
          processService,
          processingTypeServicesProvider.mapValues(_.deploymentData.deploymentManagerType),
          fingerprintService,
          scenarioActivityRepository,
          componentService,
          feStatisticsRepository,
          processingTypeServicesProvider
            .mapValues(
              _.alignedComponentsDefinitionProvider
                .getAlignedComponentsWithBuiltInComponentsAndFragments(forFragment = false, List.empty)
            )
            .all(NussknackerInternalUser.instance)
            .values
            .flatMap(_.components)
            .toList,
          clock,
          dbioRunner,
        )

        val statisticUrlConfig =
          StatisticUrlConfig(publicEncryptionKey = PublicEncryptionKey(statisticsPublicKey.mkString.trim))

        new StatisticsApiHttpService(
          authManager,
          usageStatisticsReportsSettingsService,
          feStatisticsRepository,
          statisticUrlConfig
        )
      }

      new NuDesignerExposedApiHttpService(
        appApiHttpService,
        componentsApiHttpService,
        dictApiHttpService,
        deploymentHttpService,
        migrationApiHttpService,
        nodesApiHttpService,
        testingApiHttpService,
        actionInfoHttpService,
        notificationApiHttpService,
        scenarioActivityApiHttpService,
        scenarioLabelsApiHttpService,
        scenarioParametersHttpService,
        stickyNotesApiHttpService,
        userApiHttpService,
        statisticsApiHttpService
      )
    }

    val akkaHttpServerInterpreter = new NuAkkaHttpServerInterpreterForTapirPurposes()

    createAppRoute(
      designerConfig = designerConfig,
      authManager = authManager,
      tapirRelatedRoutes = akkaHttpServerInterpreter.toRoute(nuDesignerApi.allEndpoints) :: Nil,
      apiResourcesWithAuthentication = apiResourcesWithAuthentication,
      apiResourcesWithoutAuthentication = apiResourcesWithoutAuthentication,
      developmentMode = designerConfig.featureTogglesConfig.development
    )
  }

  private def createAppRoute(
      designerConfig: DesignerConfig,
      authManager: AuthManager,
      tapirRelatedRoutes: List[Route],
      apiResourcesWithAuthentication: List[RouteWithUser],
      apiResourcesWithoutAuthentication: List[Route],
      developmentMode: Boolean
  ): Route = {
    // TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(designerConfig.rawConfig.getString("http.publicPath"))
    WithDirectives(CorsSupport.cors(developmentMode), SecurityHeadersSupport(), OptionsMethodSupport()) {
      tapirRelatedRoutes.reduce(_ ~ _) ~
        pathPrefixTest(!"api") {
          webResources.route
        } ~ pathPrefix("api") {
          apiResourcesWithoutAuthentication.reduce(_ ~ _)
        } ~ pathPrefix("api") {
          authManager.authenticate() { authenticatedUser =>
            authManager.authorizeRoute(authenticatedUser) { loggedUser =>
              apiResourcesWithAuthentication
                .map(_.securedRouteWithErrorHandling(loggedUser))
                .reduce(_ ~ _)
            }
          }
        }
    }
  }

}
