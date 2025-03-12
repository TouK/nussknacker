package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.{
  ConfigWithUnresolvedVersion,
  DeploymentManagerDependencies,
  ModelDependencies,
  ProcessingTypeConfig
}
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  AdditionalUIConfigProviderFactory,
  DesignerWideComponentId,
  EmptyAdditionalUIConfigProviderFactory
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.{
  ExecutionContextWithIORuntime,
  ExecutionContextWithIORuntimeAdapter,
  JavaClassVersionChecker,
  SLF4JBridgeHandlerRegistrar
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ScenarioStatusPresenter}
import pl.touk.nussknacker.ui.config.{
  ComponentLinksConfigExtractor,
  DesignerConfig,
  DesignerConfigLoader,
  FeatureTogglesConfig
}
import pl.touk.nussknacker.ui.configloader.{ProcessingTypeConfigsLoader, ProcessingTypeConfigsLoaderFactory}
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  ProcessServiceBasedScenarioServiceAdapter
}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.DefaultComponentService
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.notifications.Notification
import pl.touk.nussknacker.ui.process.{DBProcessService, ProcessService}
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.reconciliation.{
  FinishedDeploymentsStatusesSynchronizationConfig,
  FinishedDeploymentsStatusesSynchronizationScheduler,
  ScenarioDeploymentReconciler
}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.newdeployment.synchronize.{
  DeploymentsStatusesSynchronizationConfig,
  DeploymentsStatusesSynchronizationScheduler,
  DeploymentsStatusesSynchronizer
}
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ReloadableProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.{DbScenarioActivityRepository, ScenarioActivityRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, AuthManager, NussknackerInternalUser}
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteFactory, NussknackerHttpServer}
import pl.touk.nussknacker.ui.statistics.FingerprintService
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepositoryImpl
import pl.touk.nussknacker.ui.util.{InMemoryTimeseriesRepository, IOToFutureSttpBackendConverter}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

import java.time.{Clock, Duration}
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

object NussknackerAppFactory {

  def apply(designerConfigLoader: DesignerConfigLoader): NussknackerAppFactory = {
    new NussknackerAppFactory(designerConfigLoader)
  }

}

class NussknackerAppFactory(
    designerConfigLoader: DesignerConfigLoader,
) extends LazyLogging {

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      _ <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _ <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))

      alreadyLoadedConfig           <- Resource.eval(designerConfigLoader.loadDesignerConfig())
      system                        <- createActorSystem(alreadyLoadedConfig.rawConfig)
      executionContextWithIORuntime <- ExecutionContextWithIORuntimeAdapter.createFrom(system.dispatcher)
      ioSttpBackend                 <- AsyncHttpClientCatsBackend.resource[IO]()
      futureSttpBackend = IOToFutureSttpBackendConverter.convert(ioSttpBackend)(executionContextWithIORuntime)

      managersDirs                  <- Resource.eval(IO.delay(alreadyLoadedConfig.managersDirs()))
      deploymentManagersClassLoader <- DeploymentManagersClassLoader.create(managersDirs)
      modelClassLoaderProvider = createModelClassLoaderProvider(
        alreadyLoadedConfig.processingTypeConfigs.configByProcessingType,
        deploymentManagersClassLoader
      )

      dbRef <- DbRef.create(alreadyLoadedConfig.rawConfig.resolved)

      resolvedDesignerConfig = alreadyLoadedConfig.rawConfig.resolved
      environment            = resolvedDesignerConfig.getString("environment")
      featureTogglesConfig   = FeatureTogglesConfig.create(resolvedDesignerConfig)
      _                      = logger.info(s"Designer config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")

      actionServiceSupplier = new DelayedInitActionServiceSupplier
      additionalUIConfigProvider = createAdditionalUIConfigProvider(resolvedDesignerConfig, futureSttpBackend)(
        executionContextWithIORuntime
      )
      scenarioActivityRepository = DbScenarioActivityRepository.create(dbRef, clock)(executionContextWithIORuntime)
      dbioRunner                 = DBIOActionRunner(dbRef)(executionContextWithIORuntime)
      // 1 hour is the delay to propagate all global notifications for all users
      globalNotificationRepository = InMemoryTimeseriesRepository[Notification](Duration.ofHours(1), Clock.systemUTC())
      processingTypeDataProvider <- prepareProcessingTypeDataReload(
        alreadyLoadedConfig,
        deploymentManagersClassLoader,
        dbRef,
        system,
        ioSttpBackend,
        additionalUIConfigProvider,
        actionServiceSupplier,
        scenarioActivityRepository,
        dbioRunner,
        futureSttpBackend,
        featureTogglesConfig,
        globalNotificationRepository,
        modelClassLoaderProvider
      )(executionContextWithIORuntime)

      metricsRegistry <- createGeneralPurposeMetricsRegistry()
      feStatisticsRepository <- QuestDbFEStatisticsRepository.create(
        system,
        clock,
        alreadyLoadedConfig.rawConfig.resolved
      )
      countsReporter <- createCountsReporter(featureTogglesConfig, environment, futureSttpBackend)
      deploymentRepository = new DeploymentRepository(dbRef, clock)(executionContextWithIORuntime)
      deploymentsStatusesSynchronizer = new DeploymentsStatusesSynchronizer(
        deploymentRepository,
        processingTypeDataProvider.mapValues(
          _.deploymentData.validDeploymentManagerOrStub.deploymentSynchronisationSupport
        ),
        dbioRunner
      )(executionContextWithIORuntime)
      _ <- DeploymentsStatusesSynchronizationScheduler.resource(
        system,
        deploymentsStatusesSynchronizer,
        DeploymentsStatusesSynchronizationConfig.parse(resolvedDesignerConfig)
      )
      statisticsPublicKey <- Resource.fromAutoCloseable(
        IO {
          Source.fromURL(getClass.getResource("/encryption.key"))
        }
      )
      migrations                 = processingTypeDataProvider.mapValues(_.designerModelData.modelData.migrations)
      scenarioActivityRepository = DbScenarioActivityRepository.create(dbRef, clock)(executionContextWithIORuntime)
      actionRepository           = DbScenarioActionRepository.create(dbRef)(executionContextWithIORuntime)
      scenarioLabelsRepository   = new ScenarioLabelsRepository(dbRef)(executionContextWithIORuntime)
      processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository, scenarioLabelsRepository)(
        executionContextWithIORuntime
      )
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      futureProcessRepository =
        DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository, scenarioLabelsRepository)(
          executionContextWithIORuntime
        )
      _ = initMetrics(metricsRegistry, resolvedDesignerConfig, futureProcessRepository)

      writeProcessRepository =
        ProcessRepository.create(dbRef, clock, scenarioActivityRepository, scenarioLabelsRepository, migrations)
      dmDispatcher =
        new DeploymentManagerDispatcher(
          processingTypeDataProvider.mapValues(_.deploymentData.validDeploymentManagerOrStub),
          futureProcessRepository
        )
      fetchScenarioActivityService = new FetchScenarioActivityService(
        dmDispatcher,
        scenarioActivityRepository,
        futureProcessRepository,
        dbioRunner,
      )(executionContextWithIORuntime)
      processChangeListener = ProcessChangeListenerLoader.loadListeners(
        getClass.getClassLoader,
        resolvedDesignerConfig,
        NussknackerServices(new PullProcessRepository(futureProcessRepository, fetchScenarioActivityService))
      )
      deploymentsStatusesProvider =
        new EngineSideDeploymentStatusesProvider(dmDispatcher, featureTogglesConfig.scenarioStateTimeout)(system)
      scenarioStatusProvider = new ScenarioStatusProvider(
        deploymentsStatusesProvider,
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
      )(executionContextWithIORuntime)
      actionService = new ActionService(
        processRepository,
        actionRepository,
        dbioRunner,
        processChangeListener,
        scenarioStatusProvider,
        featureTogglesConfig.deploymentCommentSettings,
        clock
      )(executionContextWithIORuntime)

      _ = {
        // we need to reload processing type data after deployment service creation to make sure that it will be done using
        // correct classloader and that won't cause further delays during handling requests
        actionService.invalidateInProgressActions()
        actionServiceSupplier.set(actionService)
        processingTypeDataProvider.reloadAll().unsafeRunSync()(executionContextWithIORuntime.ioRuntime)
      }

      reconciler = new ScenarioDeploymentReconciler(
        processingTypeDataProvider.all(NussknackerInternalUser.instance).keys,
        deploymentsStatusesProvider,
        actionRepository,
        dbioRunner
      )(executionContextWithIORuntime)
      _ <- FinishedDeploymentsStatusesSynchronizationScheduler.resource(
        system,
        reconciler,
        FinishedDeploymentsStatusesSynchronizationConfig.parse(resolvedDesignerConfig)
      )

      authenticationResources =
        AuthenticationResources(resolvedDesignerConfig, getClass.getClassLoader, futureSttpBackend)(
          executionContextWithIORuntime
        )
      authManager = new AuthManager(authenticationResources)(executionContextWithIORuntime)

      _ = Initialization.init(
        migrations,
        dbRef,
        clock,
        processRepository,
        scenarioActivityRepository,
        scenarioLabelsRepository,
        environment
      )(executionContextWithIORuntime)

      scenarioStatusPresenter = new ScenarioStatusPresenter(dmDispatcher)

      fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)(executionContextWithIORuntime)
      fragmentResolver   = new FragmentResolver(fragmentRepository)

      counter = new ProcessCounter(fragmentRepository)

      processingTypeServicesProvider = processingTypeDataProvider.mapValues(
        ProcessingTypeServices.create(
          resolvedDesignerConfig,
          featureTogglesConfig,
          additionalUIConfigProvider,
          fragmentRepository,
          fragmentResolver,
          counter,
          _
        )(executionContextWithIORuntime)
      )

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
      )(executionContextWithIORuntime)

      customHttpServiceProviders <- createCustomHttpServiceProvider(resolvedDesignerConfig, processService)(
        executionContextWithIORuntime
      )

      fingerprintService = new FingerprintService(new FingerprintRepositoryImpl(dbRef)(executionContextWithIORuntime))(
        executionContextWithIORuntime,
        dbioRunner
      )

      componentService = {
        new DefaultComponentService(
          ComponentLinksConfigExtractor.extract(resolvedDesignerConfig),
          processingTypeServicesProvider.mapValues(_.componentServiceProcessingTypeData),
          processService,
          fragmentRepository
        )(executionContextWithIORuntime)
      }
      processAuthorizer = new AuthorizeProcess(futureProcessRepository)(executionContextWithIORuntime)
      route = new AkkaHttpBasedRouteFactory(
        clock = clock,
        dbRef = dbRef,
        dbioRunner = dbioRunner,
        metricsRegistry = metricsRegistry,
        resolvedDesignerConfig = resolvedDesignerConfig,
        featureTogglesConfig = featureTogglesConfig,
        environment = environment,
        statisticsPublicKey = statisticsPublicKey.mkString,
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
        reloadProcessingTypes = () => processingTypeDataProvider.reloadAll(),
        processAuthorizer = processAuthorizer,
        authenticationResources = authenticationResources,
        authManager = authManager,
        customHttpServiceProviders = customHttpServiceProviders
      )(
        system,
        executionContextWithIORuntime
      ).createRoute
      _ <- new NussknackerHttpServer(system).start(route, alreadyLoadedConfig, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def createProcessingTypeConfigsLoader(
      designerConfig: DesignerConfig,
      sttpBackend: SttpBackend[IO, Any]
  )(implicit ioRuntime: IORuntime): ProcessingTypeConfigsLoader = {
    ScalaServiceLoader
      .loadOne[ProcessingTypeConfigsLoaderFactory](getClass.getClassLoader)
      .map { factory =>
        logger.debug(
          s"Found custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName}: ${factory.getClass.getName}. Using it for configuration loading"
        )
        factory.create(designerConfig.configLoaderConfig, designerConfig.processingTypeConfigsRaw.resolved, sttpBackend)
      }
      .getOrElse {
        logger.debug(
          s"No custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName} found. Using the default implementation of loader"
        )
        () => designerConfigLoader.loadDesignerConfig().map(_.processingTypeConfigs)
      }
  }

  private def createActorSystem(config: ConfigWithUnresolvedVersion) = {
    Resource
      .make(
        acquire = IO(ActorSystem("nussknacker-designer", config.resolved))
      )(
        release = system => {
          IO.fromFuture(IO(system.terminate())).map(_ => ())
        }
      )
  }

  private def createGeneralPurposeMetricsRegistry() = {
    Resource.pure[IO, MetricRegistry](new MetricRegistry)
  }

  private def startJmxReporter(metricsRegistry: MetricRegistry) = {
    Resource.eval(IO(JmxReporter.forRegistry(metricsRegistry).build().start()))
  }

  private def createStartAndStopLoggingEntries() = {
    Resource
      .make(
        acquire = IO(logger.info("Nussknacker started!"))
      )(
        release = _ => IO(logger.info("Stopping Nussknacker ..."))
      )
  }

  private def createModelClassLoaderProvider(
      processingTypeConfigs: Map[String, ProcessingTypeConfig],
      deploymentManagersClassLoader: DeploymentManagersClassLoader
  ): ModelClassLoaderProvider = {
    val defaultWorkingDirOpt = None
    ModelClassLoaderProvider(
      processingTypeConfigs.mapValuesNow(c => ModelClassLoaderDependencies(c.classPath, defaultWorkingDirOpt)),
      deploymentManagersClassLoader
    )
  }

  private def initMetrics(
      metricsRegistry: MetricRegistry,
      config: Config,
      processRepository: DBFetchingProcessRepository[Future] with BasicRepository
  ): Unit = {
    new RepositoryGauges(metricsRegistry, config.getDuration("repositoryGaugesCacheDuration"), processRepository)
      .prepareGauges()
  }

  private def createCountsReporter(
      featureTogglesConfig: FeatureTogglesConfig,
      environment: String,
      backend: SttpBackend[Future, Any]
  ) = {
    featureTogglesConfig.counts match {
      case Some(config) => prepareCountsReporter(environment, config, backend)
      case None         => Resource.pure[IO, None.type](None)
    }
  }

  // by default, we use InfluxCountsReporterCreator
  private def prepareCountsReporter(
      env: String,
      config: Config,
      backend: SttpBackend[Future, Any]
  ): Resource[IO, Option[CountsReporter[Future]]] = {
    Resource
      .make(
        acquire = IO {
          val configAtKey = config.atKey(CountsReporterCreator.reporterCreatorConfigPath)
          val creator = Multiplicity(ScalaServiceLoader.load[CountsReporterCreator](getClass.getClassLoader)) match {
            case One(cr) =>
              cr
            case Empty() =>
              new InfluxCountsReporterCreator
            case Many(many) =>
              throw new IllegalArgumentException(s"Many CountsReporters found: ${many.mkString(", ")}")
          }

          Try(Option(creator.createReporter(env, configAtKey)(backend))).recover { case NonFatal(ex) =>
            logger.warn(
              s"Error while setting up counts mechanism: ${ex.getMessage}. Counts mechanism will be disabled."
            )
            None
          }.get
        }
      )(
        release = counter => IO(counter.foreach(_.close()))
      )
  }

  private def prepareProcessingTypeDataReload(
      alreadyLoadedConfig: DesignerConfig,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      dbRef: DbRef,
      system: ActorSystem,
      ioSttpBackend: SttpBackend[IO, Any],
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      actionServiceProvider: Supplier[ActionService],
      scenarioActivityRepository: ScenarioActivityRepository,
      dbioActionRunner: DBIOActionRunner,
      sttpBackend: SttpBackend[Future, Any],
      featureTogglesConfig: FeatureTogglesConfig,
      globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
      modelClassLoaderProvider: ModelClassLoaderProvider
  )(
      implicit executionContextWithIORuntime: ExecutionContextWithIORuntime
  ): Resource[IO, ReloadableProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData]] = {
    Resource
      .make(
        acquire = IO {
          val processingTypeConfigsLoader = createProcessingTypeConfigsLoader(
            alreadyLoadedConfig,
            ioSttpBackend
          )(executionContextWithIORuntime.ioRuntime)
          val processingTypeDataLoader = new ProcessingTypeDataLoader(processingTypeConfigsLoader)
          val loadProcessingTypeDataIO = processingTypeDataLoader.loadProcessingTypeData(
            getModelDependencies(
              additionalUIConfigProvider,
              _,
              featureTogglesConfig.componentDefinitionExtractionMode
            ),
            getDeploymentManagerDependencies(
              dbRef,
              system,
              additionalUIConfigProvider,
              actionServiceProvider,
              scenarioActivityRepository,
              dbioActionRunner,
              sttpBackend,
              _
            ),
            deploymentManagersClassLoader,
            modelClassLoaderProvider,
            Some(dbRef),
          )
          val loadAndNotifyIO = loadProcessingTypeDataIO
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
      dbRef: DbRef,
      system: ActorSystem,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      actionServiceProvider: Supplier[ActionService],
      scenarioActivityRepository: ScenarioActivityRepository,
      dbioActionRunner: DBIOActionRunner,
      sttpBackend: SttpBackend[Future, Any],
      processingType: ProcessingType
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    DeploymentManagerDependencies(
      DefaultProcessingTypeDeployedScenariosProvider(dbRef, processingType),
      new DefaultProcessingTypeActionService(
        processingType,
        actionServiceProvider.get(),
      ),
      new RepositoryBasedScenarioActivityManager(
        scenarioActivityRepository,
        dbioActionRunner,
      ),
      executionContextWithIORuntime,
      executionContextWithIORuntime.ioRuntime,
      system,
      sttpBackend,
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

  private def createAdditionalUIConfigProvider(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ) = {
    val additionalUIConfigProviderFactory: AdditionalUIConfigProviderFactory = {
      Multiplicity(
        ScalaServiceLoader.load[AdditionalUIConfigProviderFactory](getClass.getClassLoader)
      ) match {
        case Empty()              => new EmptyAdditionalUIConfigProviderFactory
        case One(providerFactory) => providerFactory
        case Many(moreThanOne) =>
          throw new IllegalArgumentException(
            s"More than one AdditionalUIConfigProviderFactory instance found: $moreThanOne"
          )
      }
    }

    additionalUIConfigProviderFactory.create(config, sttpBackend)
  }

  private def createCustomHttpServiceProvider(
      config: Config,
      processService: ProcessService
  )(implicit ec: ExecutionContext): Resource[IO, Map[String, CustomHttpServiceProvider]] = {
    lazy val nussknackerServices = new NussknackerServicesForCustomHttpService(
      new ProcessServiceBasedScenarioServiceAdapter(processService)
    )

    loadCustomHttpServiceProviderFactories()
      .map { factory => factory.create(config, nussknackerServices).map(factory.name -> _) }
      .sequence
      .map(_.toMap)
  }

  private def loadCustomHttpServiceProviderFactories(): List[CustomHttpServiceProviderFactory] = {
    Multiplicity(
      ScalaServiceLoader.load[CustomHttpServiceProviderFactory](getClass.getClassLoader)
    ) match {
      case Empty() =>
        List.empty[CustomHttpServiceProviderFactory]
      case One(providerFactory) =>
        List(providerFactory)
      case Many(moreThanOne) if moreThanOne.map(_.name).distinct.size == moreThanOne.size =>
        moreThanOne
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(
          s"CustomHttpServiceProviderFactory instances with conflicting names found: $moreThanOne"
        )
    }
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
