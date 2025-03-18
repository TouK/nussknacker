package pl.touk.nussknacker.ui.factory

import cats.effect.{IO, Resource}
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.processCounts.CountsReporter
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ScenarioStatusPresenter}
import pl.touk.nussknacker.ui.config.{AdditionalUIConfigProviderLoader, DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.{ComponentService, DefaultComponentService}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, ProcessChangeListenerLoader}
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.notifications.Notification
import pl.touk.nussknacker.ui.process.{DBProcessService, ProcessService}
import pl.touk.nussknacker.ui.process.deployment.{
  ActionService,
  DefaultProcessingTypeDeployedScenariosProvider,
  DeploymentManagerDispatcher
}
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.reconciliation.{
  FinishedDeploymentsStatusesSynchronizationScheduler,
  ScenarioDeploymentReconciler
}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.newdeployment.synchronize.{
  DeploymentsStatusesSynchronizationScheduler,
  DeploymentsStatusesSynchronizer
}
import pl.touk.nussknacker.ui.process.periodic.{DefaultProcessingTypeActionService, SchedulingDependencies}
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
import scala.concurrent.Future

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
)

object DomainServices {

  def create(
      designerConfigLoader: DesignerConfigLoader,
      alreadyLoadedConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices
  ): Resource[IO, DomainServices] = {
    import infrastructureServices._
    for {
      deploymentManagersClassLoader <- DeploymentManagersClassLoader.create(alreadyLoadedConfig.managersDir)
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
        additionalUIConfigProvider,
        globalNotificationRepository,
        modelClassLoaderProvider
      )
      actionServiceSupplier    = new DelayedInitActionServiceSupplier
      actionRepository         = DbScenarioActionRepository.create(dbRef)
      scenarioLabelsRepository = new ScenarioLabelsRepository(dbRef)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      futureProcessRepository = DBFetchingProcessRepository.createFutureRepository(
        dbRef,
        actionRepository,
        scenarioLabelsRepository
      )
      deploymentData <- DeploymentManagersLoader.load(
        alreadyLoadedConfig.processingTypeConfigs(),
        deploymentManagersClassLoader,
        modelClassLoaderProvider,
        modelDataProvider.mapValues(_.modelData),
        getDeploymentManagerDependencies(
          infrastructureServices,
          _
        ),
        Some(
          getSchedulingDependencies(
            infrastructureServices,
            actionServiceSupplier,
            futureProcessRepository,
            additionalUIConfigProvider,
            _
          )
        )
      )
      deploymentDataProvider = ProcessingTypeDataProvider.fromState(
        ProcessingTypeDataState.withUninitializedCombinedData(deploymentData)
      )
      deploymentRepository       = new DeploymentRepository(dbRef, clock)
      scenarioActivityRepository = DbScenarioActivityRepository.create(dbRef, clock)
      dmDispatcher =
        new DeploymentManagerDispatcher(
          deploymentDataProvider.mapValues(_.validDeploymentManagerOrStub),
          futureProcessRepository
        )
      fetchScenarioActivityService = new FetchScenarioActivityService(
        dmDispatcher,
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
        )(actorSystem)
      processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository, scenarioLabelsRepository)
      scenarioStatusProvider = new ScenarioStatusProvider(
        deploymentsStatusesProvider,
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
      )
      actionService = new ActionService(
        processRepository,
        actionRepository,
        dbioRunner,
        processChangeListener,
        scenarioStatusProvider,
        alreadyLoadedConfig.deploymentCommentSettings,
        clock
      )
      _ = {
        actionService.invalidateInProgressActions()
        actionServiceSupplier.set(actionService)
      }
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

      reconciler = new ScenarioDeploymentReconciler(
        deploymentData.keys,
        deploymentsStatusesProvider,
        actionRepository,
        dbioRunner
      )
      _ <- FinishedDeploymentsStatusesSynchronizationScheduler.resource(
        actorSystem,
        reconciler,
        alreadyLoadedConfig.finishedDeploymentStatusesSynchronization
      )

      scenarioStatusPresenter = new ScenarioStatusPresenter(dmDispatcher)

      fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)
      fragmentResolver   = new FragmentResolver(fragmentRepository)

      counter = new ProcessCounter(fragmentRepository)

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
        scenarioStatusProvider,
        scenarioStatusPresenter,
        processingTypeServicesProvider.mapValues(_.newProcessPreparer),
        processingTypeDataProvider.mapCombined(_.parametersService),
        processingTypeServicesProvider.mapValues(_.processResolver),
        dbioRunner,
        futureProcessRepository,
        actionRepository,
        writeProcessRepository,
      )

      fingerprintService = new FingerprintService(new FingerprintRepositoryImpl(dbRef))(
        executionContextWithIORuntime,
        dbioRunner
      )

      componentService = {
        new DefaultComponentService(
          alreadyLoadedConfig.componentLinks,
          processingTypeServicesProvider.mapValues(_.componentServiceProcessingTypeData),
          processService,
          fragmentRepository
        )
      }

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
      scenarioStatusProvider = scenarioStatusProvider,
      scenarioStatusPresenter = scenarioStatusPresenter,
      dmDispatcher = dmDispatcher,
      processingTypeServicesProvider = processingTypeServicesProvider,
      reloadModelData = modelDataProvider.reloadAll,
      processAuthorizer = processAuthorizer,
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
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
      modelClassLoaderProvider: ModelClassLoaderProvider
  ): Resource[IO, ReloadableProcessingTypeDataProvider[ModelDataWithProcessingTypeDataInput, _]] = {
    Resource
      .make(
        acquire = IO {
          val loadModelDataIO = designerConfigLoader
            .loadDesignerConfig()
            .map(_.processingTypeConfigs())
            .map(
              ModelDataLoader.load(
                _,
                getModelDependencies(
                  additionalUIConfigProvider,
                  _,
                  alreadyLoadedConfig.componentDefinitionExtractionMode
                ),
                modelClassLoaderProvider,
              )
            )
          val loadAndNotifyIO = loadModelDataIO
            .map { state =>
              globalNotificationRepository.saveEntry(Notification.configurationReloaded)
              state
            }
          ReloadableProcessingTypeDataProvider(loadAndNotifyIO)
        }
      )(
        release = _.close()
      )
  }

  private def getDeploymentManagerDependencies(
      infrastructureServices: InfrastructureServices,
      processingType: ProcessingType
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime) = {
    DeploymentManagerDependencies(
      DefaultProcessingTypeDeployedScenariosProvider(infrastructureServices.dbRef, processingType),
      executionContextWithIORuntime,
      executionContextWithIORuntime.ioRuntime,
      infrastructureServices.actorSystem,
      infrastructureServices.futureSttpBackend
    )
  }

  private def getSchedulingDependencies(
      infrastructureServices: InfrastructureServices,
      actionServiceProvider: Supplier[ActionService],
      fetchingProcessRepository: FetchingProcessRepository[Future],
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      processingType: ProcessingType
  ) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    new SchedulingDependencies(
      infrastructureServices.dbRef,
      new DefaultProcessingTypeActionService(processingType, actionServiceProvider),
      fetchingProcessRepository,
      additionalConfigsFromProvider
    )
  }

  private def getModelDependencies(
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      processingType: ProcessingType,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    ModelDependencies(
      additionalConfigsFromProvider,
      DesignerWideComponentId.default(processingType, _),
      workingDirectoryOpt = None, // we use the default working directory
      componentDefinitionExtractionMode,
    )
  }

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

}
