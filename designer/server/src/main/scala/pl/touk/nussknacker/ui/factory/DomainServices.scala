package pl.touk.nussknacker.ui.factory

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.classloader.{DeploymentManagersClassLoader, DeploymentManagersClassLoaderFactory}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.processCounts.CountsReporter
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ScenarioStatusPresenter}
import pl.touk.nussknacker.ui.config.{AdditionalUIConfigProviderLoader, DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.{ComponentService, DefaultComponentService}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.limits.LimitsService
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, ProcessChangeListenerLoader}
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.notifications.Notification
import pl.touk.nussknacker.ui.process.{DBProcessService, ProcessService}
import pl.touk.nussknacker.ui.process.deployment.{ActionService, DeploymentManagerDispatcher}
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.reconciliation.{
  FinishedDeploymentsStatusesSynchronizationScheduler,
  ScenarioDeploymentReconciler
}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.livedata.{DbLiveDataRepository, LiveDataRepository}
import pl.touk.nussknacker.ui.process.draft.ProcessDraftService
import pl.touk.nussknacker.ui.process.repository.DbProcessDraftRepository
import pl.touk.nussknacker.ui.process.newdeployment
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.newdeployment.synchronize.{
  DeploymentsStatusesSynchronizationScheduler,
  DeploymentsStatusesSynchronizer
}
import pl.touk.nussknacker.ui.process.periodic.{
  DefaultProcessingTypeActionService,
  DefaultSchedulingScenarioActivitiesRepository,
  SchedulingDependencies
}
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.loader.{
  DeploymentManagersLoader,
  ModelDataLoader,
  ProcessingTypeDataStateFactory
}
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataStateFactory.ModelDataWithProcessingTypeDataInput
import pl.touk.nussknacker.ui.process.processingtype.provider.{
  ProcessingTypeDataProvider,
  ProcessingTypeDataState,
  ReloadableProcessingTypeDataProvider
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.{DbScenarioActivityRepository, ScenarioActivityRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.processreport.{CountsReporterFactory, ProcessCounter}
import pl.touk.nussknacker.ui.statistics.FingerprintService
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepositoryImpl
import pl.touk.nussknacker.ui.util.InMemoryTimeseriesRepository

import java.time.{Clock, Duration}
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final class DomainServices(
    val futureProcessRepository: FetchingProcessRepository[Future],
    val scenarioActivityRepository: ScenarioActivityRepository,
    val scenarioLabelsRepository: ScenarioLabelsRepository,
    val globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
    val feStatisticsRepository: FEStatisticsRepository[Future],
    val componentService: ComponentService,
    val processService: ProcessService,
    val fetchScenarioActivityService: FetchScenarioActivityService,
    val actionRepository: ScenarioActionRepository,
    val deploymentRepository: DeploymentRepository,
    val processChangeListener: ProcessChangeListener,
    val actionService: ActionService,
    val countsReporter: Option[CountsReporter[Future]],
    val counter: ProcessCounter,
    val fingerprintService: FingerprintService,
    val scenarioStatusProvider: ScenarioStatusProvider,
    val scenarioStatusPresenter: ScenarioStatusPresenter,
    val dmDispatcher: DeploymentManagerDispatcher,
    val processingTypeServicesProvider: ProcessingTypeDataProvider[ProcessingTypeServices, CombinedProcessingTypeData],
    val reloadModelData: IO[Unit],
    val processAuthorizer: AuthorizeProcess,
    val limitsService: LimitsService,
    val processCounter: ProcessCounter,
    val liveDataRepository: LiveDataRepository,
    val processDraftService: ProcessDraftService,
    val reconciler: ScenarioDeploymentReconciler
)

object DomainServices extends LazyLogging {

  def create(
      designerConfigLoader: DesignerConfigLoader,
      alreadyLoadedConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices
  ): Resource[IO, DomainServices] = {
    import infrastructureServices._
    for {
      // services for model data purpose
      deploymentManagersClassLoader <- DeploymentManagersClassLoaderFactory.create(alreadyLoadedConfig.managersDir)
      modelClassLoaderProvider = createModelClassLoaderProvider(
        alreadyLoadedConfig.processingTypeConfigs(),
        deploymentManagersClassLoader
      )

      additionalUIConfigProvider = AdditionalUIConfigProviderLoader.loadAdditionalUIConfigProvider(
        alreadyLoadedConfig,
        futureSttpBackend
      )
      // 1 hour is the delay to propagate all global notifications for all users
      globalNotificationRepository = InMemoryTimeseriesRepository[Notification](Duration.ofHours(1), Clock.systemUTC())
      modelDataProvider <- prepareModelDataReload(
        designerConfigLoader,
        alreadyLoadedConfig,
        infrastructureServices,
        modelClassLoaderProvider,
        additionalUIConfigProvider,
        globalNotificationRepository,
      )
      // deployment data deps
      actionServiceSupplier    = new DelayedInitActionServiceSupplier
      actionRepository         = DbScenarioActionRepository.create(dbRef)
      scenarioLabelsRepository = new ScenarioLabelsRepository(dbRef)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      futureProcessRepository = DBFetchingProcessRepository.createFutureRepository(
        dbRef,
        actionRepository,
        scenarioLabelsRepository
      )
      scenarioActivityRepository = DbScenarioActivityRepository.create(dbRef, clock)
      deploymentData <- DeploymentManagersLoader.load(
        alreadyLoadedConfig.processingTypeConfigs(),
        deploymentManagersClassLoader,
        modelClassLoaderProvider,
        modelDataProvider.mapValues(_.modelData),
        infrastructureServices.deploymentManagerDependencies,
        Some(
          getSchedulingDependencies(
            infrastructureServices,
            actionServiceSupplier,
            futureProcessRepository,
            scenarioActivityRepository,
            dbioRunner,
            additionalUIConfigProvider,
            _
          )
        )
      )
      // ActionService initialization
      // We wrap deploymentData with ProcessingTypeDataProvider to allow category restriction checking. These data are static, not reloadable
      deploymentDataProvider <-
        Resource.make(
          IO(
            ProcessingTypeDataProvider.fromState(
              ProcessingTypeDataState.withUninitializedCombinedData(deploymentData)
            )
          )
        )(_.close())

      dmDispatcher =
        new DeploymentManagerDispatcher(
          deploymentDataProvider.mapValues(_.validDeploymentManagerOrStub),
          futureProcessRepository
        )
      fetchScenarioActivityService = new FetchScenarioActivityService(
        scenarioActivityRepository,
        futureProcessRepository,
        dbioRunner,
      )
      processChangeListener = ProcessChangeListenerLoader.loadListeners(
        DomainServices.getClass.getClassLoader,
        alreadyLoadedConfig,
        NussknackerServices(new PullProcessRepository(futureProcessRepository, fetchScenarioActivityService))
      )
      deploymentsStatusesProvider =
        new EngineSideDeploymentStatusesProvider(
          dmDispatcher,
          alreadyLoadedConfig.scenarioStateTimeout
        )
      processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository, scenarioLabelsRepository)
      oldApproachScenarioStatusProvider = new ScenarioStatusProvider(
        deploymentsStatusesProvider,
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
      )

      deploymentRepository = new DeploymentRepository(dbRef, clock)
      deploymentsStatusesSynchronizer = new DeploymentsStatusesSynchronizer(
        deploymentRepository,
        deploymentDataProvider.mapValues(
          _.validDeploymentManagerOrStub.deploymentSynchronisationSupport
        ),
        dbioRunner
      )
      _ <- DeploymentsStatusesSynchronizationScheduler.resource(
        actorSystem,
        deploymentsStatusesSynchronizer,
        alreadyLoadedConfig.deploymentsStatusesSynchronizationConfig
      )

      scenarioStatusPresenter = new ScenarioStatusPresenter(dmDispatcher)

      fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)
      fragmentResolver   = new FragmentResolver(fragmentRepository)

      counter = new ProcessCounter(fragmentRepository)

      processAuthorizer = new AuthorizeProcess(futureProcessRepository)

      feStatisticsRepository <- QuestDbFEStatisticsRepository.create(
        actorSystem,
        clock,
        alreadyLoadedConfig.questDbSettings
      )

      countsReporter <- CountsReporterFactory.createCountsReporter(
        alreadyLoadedConfig,
        futureSttpBackend
      )

      fingerprintService = new FingerprintService(new FingerprintRepositoryImpl(dbRef))(
        executionContextWithIORuntime,
        dbioRunner
      )

      // ProcessingTypeData-related services
      processingTypeDataProvider = modelDataProvider.transform { case (modelDataWithInputs, _) =>
        ProcessingTypeDataStateFactory
          .create(
            modelDataWithInputs,
            deploymentData
          )
          .toEither
          .toTry
          .get
      }

      processingTypeServicesProvider = processingTypeDataProvider.mapValues(
        ProcessingTypeServices.create(
          alreadyLoadedConfig,
          additionalUIConfigProvider,
          fragmentRepository,
          fragmentResolver,
          counter,
          _
        )
      )

      migrations = processingTypeDataProvider.mapValues(_.designerModelData.modelData.migrations)
      writeProcessRepository =
        ProcessRepository.create(dbRef, clock, scenarioActivityRepository, scenarioLabelsRepository, migrations)

      processService = new DBProcessService(
        oldApproachScenarioStatusProvider,
        scenarioStatusPresenter,
        processingTypeServicesProvider.mapValues(_.newProcessPreparer),
        processingTypeDataProvider.mapCombined(_.parametersService),
        processingTypeServicesProvider.mapValues(_.processResolver),
        dbioRunner,
        futureProcessRepository,
        actionRepository,
        writeProcessRepository,
      )

      actionService = new ActionService(
        processRepository,
        actionRepository,
        dbioRunner,
        processChangeListener,
        oldApproachScenarioStatusProvider,
        alreadyLoadedConfig.deploymentCommentSettings,
        clock,
        processService,
        processingTypeDataProvider.mapCombined(_.parametersService),
      )
      _ = {
        actionService.invalidateInProgressActions()
        actionServiceSupplier.set(actionService)
      }
      // end of ActionService initialization

      componentService = {
        new DefaultComponentService(
          alreadyLoadedConfig.componentLinks,
          processingTypeServicesProvider.mapValues(_.componentServiceProcessingTypeData),
          processService,
          fragmentRepository
        )
      }

      reconciler = new ScenarioDeploymentReconciler(
        processingTypeServicesProvider.mapValues(services =>
          new ScenarioDeploymentReconciler.ProcessingTypeServicesDeps(
            deploymentManager = services.deploymentData.validDeploymentManagerOrStub,
            engineSetupName = services.deploymentData.engineSetupName,
            jobsRecoverySettings = services.deploymentData.jobsRecoverySettings,
            scenarioResolver = services.scenarioResolver
          )
        ),
        deploymentsStatusesProvider,
        actionRepository,
        futureProcessRepository,
        dbioRunner
      )
      _ <- FinishedDeploymentsStatusesSynchronizationScheduler.resource(
        actorSystem,
        reconciler,
        alreadyLoadedConfig.finishedDeploymentStatusesSynchronization
      )
      limitsService = createLimitsService(
        alreadyLoadedConfig,
        processingTypeServicesProvider,
        oldApproachScenarioStatusProvider,
        deploymentRepository,
        dbioRunner
      )
      liveDataRepository = new DbLiveDataRepository(dbRef)
      processDraftRepository = new DbProcessDraftRepository(dbRef)
      processDraftService = new ProcessDraftService(
        processDraftRepository,
        dbioRunner,
        globalNotificationRepository,
      )
      _ = Initialization.init(
        migrations,
        dbRef,
        clock,
        processRepository,
        scenarioActivityRepository,
        scenarioLabelsRepository,
        alreadyLoadedConfig.environment
      )
    } yield new DomainServices(
      futureProcessRepository = futureProcessRepository,
      scenarioActivityRepository = scenarioActivityRepository,
      scenarioLabelsRepository = scenarioLabelsRepository,
      globalNotificationRepository = globalNotificationRepository,
      feStatisticsRepository = feStatisticsRepository,
      componentService = componentService,
      processService = processService,
      fetchScenarioActivityService = fetchScenarioActivityService,
      actionRepository = actionRepository,
      deploymentRepository = deploymentRepository,
      processChangeListener = processChangeListener,
      actionService = actionService,
      countsReporter = countsReporter,
      counter = counter,
      fingerprintService = fingerprintService,
      scenarioStatusProvider = oldApproachScenarioStatusProvider,
      scenarioStatusPresenter = scenarioStatusPresenter,
      dmDispatcher = dmDispatcher,
      processingTypeServicesProvider = processingTypeServicesProvider,
      reloadModelData = modelDataProvider.reloadAll,
      processAuthorizer = processAuthorizer,
      limitsService = limitsService,
      processCounter = counter,
      liveDataRepository = liveDataRepository,
      processDraftService = processDraftService,
      reconciler = reconciler
    )
  }

  private def createModelClassLoaderProvider(
      processingTypeConfigs: ProcessingTypeConfigs,
      deploymentManagersClassLoader: DeploymentManagersClassLoader
  ): ModelClassLoaderProvider = {
    val defaultWorkingDirOpt = None
    ModelClassLoaderProvider(
      processingTypeConfigs.configByProcessingType.mapValuesNow(c =>
        ModelClassLoaderDependencies(c.classPath, defaultWorkingDirOpt)
      ),
      deploymentManagersClassLoader
    )
  }

  private def prepareModelDataReload(
      designerConfigLoader: DesignerConfigLoader,
      alreadyLoadedConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
  ): Resource[IO, ReloadableProcessingTypeDataProvider[ModelDataWithProcessingTypeDataInput, _]] = {
    import infrastructureServices._
    Resource
      .make(
        acquire = {
          val processingTypeConfigsLoader = ProcessingTypeConfigsLoaderLoader.createProcessingTypeConfigsLoader(
            designerConfigLoader,
            alreadyLoadedConfig,
            infrastructureServices.ioSttpBackend
          )
          val loadModelDataIO = processingTypeConfigsLoader
            .loadProcessingTypeConfigs()
            .map(
              ModelDataLoader.load(
                _,
                getModelDependencies(
                  additionalUIConfigProvider,
                  _,
                  alreadyLoadedConfig.componentDefinitionExtractionMode,
                  infrastructureServices,
                ),
                modelClassLoaderProvider,
              )
            )
          val loadAndNotifyIO = loadModelDataIO
            .map { state =>
              globalNotificationRepository.saveEntry(Notification.configurationReloaded)
              state
            }
          ReloadableProcessingTypeDataProvider.create(loadAndNotifyIO)
        }
      )(
        release = _.close()
      )
  }

  private def getSchedulingDependencies(
      infrastructureServices: InfrastructureServices,
      actionServiceProvider: Supplier[ActionService],
      fetchingProcessRepository: FetchingProcessRepository[Future],
      scenarioActivityRepository: ScenarioActivityRepository,
      dbioActionRunner: DBIOActionRunner,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      processingType: ProcessingType
  ) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    val schedulingScenarioActivitiesRepository = new DefaultSchedulingScenarioActivitiesRepository(
      activitiesRepository = scenarioActivityRepository,
      dbioActionRunner = dbioActionRunner
    )
    new SchedulingDependencies(
      infrastructureServices.dbRef,
      new DefaultProcessingTypeActionService(processingType, actionServiceProvider),
      fetchingProcessRepository,
      schedulingScenarioActivitiesRepository,
      additionalConfigsFromProvider,
    )
  }

  private def getModelDependencies(
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      processingType: ProcessingType,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode,
      infrastructureServices: InfrastructureServices
  ) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    ModelDependencies(
      additionalConfigsFromProvider,
      DesignerWideComponentId.default(processingType, _),
      workingDirectoryOpt = None, // we use the default working directory
      componentDefinitionExtractionMode,
      Some(infrastructureServices.dbRef)
    )
  }

  private def createLimitsService(
      designerConfig: DesignerConfig,
      processingTypeServicesProvider: ProcessingTypeDataProvider[ProcessingTypeServices, CombinedProcessingTypeData],
      scenarioStatusProvider: ScenarioStatusProvider,
      deploymentRepository: DeploymentRepository,
      dbioRunner: DBIOActionRunner
  ) = {
    new LimitsService(
      globalLimitsConfig = designerConfig.globalLimitsConfig,
      perProcessingTypesLimitsProvider = processingTypeServicesProvider.mapValues(_.limitsConfig),
      oldDeploymentsApproachScenarioStatusProvider = scenarioStatusProvider,
      newDeploymentsApproachScenarioStatusProvider = new newdeployment.ScenarioStatusProvider(
        processingTypeChecker = processingTypeServicesProvider,
        deploymentRepository = deploymentRepository,
        dbioRunner = dbioRunner
      )
    )
  }

  // This hack with delayed init is needed because we have a cycle of dependencies:
  // DeploymentManagerDispatcher -> DeploymentData -> PeriodicDeploymentManagerDecorator -> ProcessingTypeActionService.markActionExecutionFinished ->
  // ActionService -> ProcessChangeListener -> FetchScenarioActivityService ->
  // (to check if DM has ManagerSpecificScenarioActivitiesStoredByManager which are only for scheduling mechanism purpose) -> DeploymentManagerDispatcher
  // TODO: scheduling mechanism shouldn't be implemented as DeploymentManager decorator
  private class DelayedInitActionServiceSupplier extends Supplier[ActionService] {
    private val actionServiceRef = new AtomicReference[Option[ActionService]](None)

    override def get(): ActionService = {
      val actionService = actionServiceRef.get()
      actionService.getOrElse(
        throw new IllegalStateException(
          "Illegal initialization: ActionService should be initialized before ProcessingTypeData"
        )
      )
    }

    def set(actionService: ActionService): Unit = actionServiceRef.set(Some(actionService))
  }

  private implicit class InfrastructureServicesOps(infrastructureServices: InfrastructureServices) {

    lazy val deploymentManagerDependencies: DeploymentManagerDependencies = {
      new DeploymentManagerDependencies(
        infrastructureServices.executionContextWithIORuntime,
        infrastructureServices.executionContextWithIORuntime.ioRuntime,
        infrastructureServices.actorSystem,
        infrastructureServices.futureSttpBackend
      )
    }

  }

}
