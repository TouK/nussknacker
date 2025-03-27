package pl.touk.nussknacker.ui.server

import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.migrations.{MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.notifications.NotificationServiceImpl
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService
import pl.touk.nussknacker.ui.process.label.ScenarioLabelsService
import pl.touk.nussknacker.ui.process.newactivity.ActivityService
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService
import pl.touk.nussknacker.ui.process.repository.ScenarioMetadataRepository
import pl.touk.nussknacker.ui.process.repository.stickynotes.DbStickyNotesRepository
import pl.touk.nussknacker.ui.process.version.{ScenarioGraphVersionRepository, ScenarioGraphVersionService}
import pl.touk.nussknacker.ui.security.api.{AuthManager, NussknackerInternalUser}
import pl.touk.nussknacker.ui.statistics.{
  PublicEncryptionKey,
  StatisticUrlConfig,
  UsageStatisticsReportsSettingsService
}
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator

object TapirHttpServiceFactory {

  def createHttpService(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices,
      authManager: AuthManager
  ): NuDesignerExposedApiHttpService = {
    import domainServices._
    import infrastructureServices._

    val appApiHttpService = new AppApiHttpService(
      designerConfig = designerConfig,
      authManager = authManager,
      reloadModelData = reloadModelData,
      modelInfos = processingTypeServicesProvider.mapValues(_.designerModelData.modelData.info),
      categories = processingTypeServicesProvider.mapValues(_.category),
      processService = processService,
      shouldExposeConfig = designerConfig.enableConfigEndpoint,
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

    val versionControlApiHttpService = new VersionControlApiHttpService(
      authManager = authManager,
      processService = processService
    )

    val scenarioLabelsApiHttpService = new ScenarioLabelsApiHttpService(
      authManager = authManager,
      service = new ScenarioLabelsService(
        scenarioLabelsRepository,
        new ScenarioLabelsValidator(designerConfig.scenarioLabelConfig),
        dbioRunner
      )
    )

    val notificationApiHttpService = {
      val notificationService = new NotificationServiceImpl(
        fetchScenarioActivityService,
        actionRepository,
        globalNotificationRepository,
        dbioRunner,
        designerConfig.notifications
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
      val stickyNotesRepository = DbStickyNotesRepository.create(dbRef, clock)
      new StickyNotesApiHttpService(
        authManager = authManager,
        stickyNotesRepository = stickyNotesRepository,
        scenarioService = processService,
        scenarioAuthorizer = processAuthorizer,
        dbioRunner,
        stickyNotesSettings = designerConfig.stickyNotesSettings
      )
    }

    val scenarioActivityApiHttpService = new ScenarioActivityApiHttpService(
      authManager = authManager,
      fetchScenarioActivityService = fetchScenarioActivityService,
      scenarioActivityRepository = scenarioActivityRepository,
      scenarioService = processService,
      scenarioAuthorizer = processAuthorizer,
      new ScenarioAttachmentService(
        designerConfig.attachments,
        scenarioActivityRepository,
        dbioRunner,
      ),
      designerConfig.deploymentCommentSettings,
      new PekkoHttpBasedTapirStreamEndpointProvider(),
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
          clock,
          processingTypeServicesProvider.mapValues(_.additionalComponentConfigs)
        )
      val activityService =
        new ActivityService(
          designerConfig.deploymentCommentSettings,
          scenarioActivityRepository,
          deploymentService,
          dbioRunner,
          clock,
        )
      new DeploymentApiHttpService(authManager, activityService, deploymentService)
    }

    val statisticsApiHttpService = {
      val usageStatisticsReportsSettingsService = UsageStatisticsReportsSettingsService(
        designerConfig.usageStatisticsReportsConfig,
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

      val statisticsPublicKey = ResourceLoader.load("/encryption.key")
      val statisticsUrlConfig =
        StatisticUrlConfig(publicEncryptionKey = PublicEncryptionKey(statisticsPublicKey.mkString.trim))

      new StatisticsApiHttpService(
        authManager,
        usageStatisticsReportsSettingsService,
        feStatisticsRepository,
        statisticsUrlConfig
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
      versionControlApiHttpService,
      statisticsApiHttpService
    )
  }

}
