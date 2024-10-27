package pl.touk.nussknacker.ui.server

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.test.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.config.{
  AttachmentsConfig,
  ComponentLinksConfigExtractor,
  FeatureTogglesConfig,
  UsageStatisticsReportsConfig
}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.{ComponentServiceProcessingTypeData, DefaultComponentService}
import pl.touk.nussknacker.ui.definition.{
  AlignedComponentsDefinitionProvider,
  DefinitionsService,
  ScenarioPropertiesConfigFinalizer
}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.initialization.Initialization.nussknackerUser
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.migrations.{MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.notifications.{NotificationConfig, NotificationServiceImpl}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.{
  ActionService,
  DefaultProcessingTypeActionService,
  DefaultProcessingTypeDeployedScenariosProvider,
  DeploymentManagerDispatcher,
  DeploymentService => LegacyDeploymentService,
  RepositoryBasedScenarioActivityManager,
  ScenarioResolver,
  ScenarioTestExecutorServiceImpl
}
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.label.ScenarioLabelsService
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.process.newactivity.ActivityService
import pl.touk.nussknacker.ui.process.newdeployment.synchronize.{
  DeploymentsStatusesSynchronizationConfig,
  DeploymentsStatusesSynchronizationScheduler,
  DeploymentsStatusesSynchronizer
}
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentRepository, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ReloadableProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.{DbScenarioActivityRepository, ScenarioActivityRepository}
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.process.version.{ScenarioGraphVersionRepository, ScenarioGraphVersionService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{AuthManager, AuthenticationResources}
import pl.touk.nussknacker.ui.services.{ManagementApiHttpService, NuDesignerExposedApiHttpService}
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepositoryImpl
import pl.touk.nussknacker.ui.statistics.{
  FingerprintService,
  PublicEncryptionKey,
  StatisticUrlConfig,
  UsageStatisticsReportsSettingsService
}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util._
import pl.touk.nussknacker.ui.validation.{
  NodeValidator,
  ParametersValidator,
  ScenarioLabelsValidator,
  UIProcessValidator
}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

class AkkaHttpBasedRouteProvider(
    dbRef: DbRef,
    metricsRegistry: MetricRegistry,
    processingTypeDataLoader: ProcessingTypeDataLoader,
    feStatisticsRepository: FEStatisticsRepository[Future],
    designerClock: Clock
)(implicit system: ActorSystem, materializer: Materializer)
    extends RouteProvider[Route]
    with Directives
    with LazyLogging {

  override def createRoute(config: ConfigWithUnresolvedVersion): Resource[IO, Route] = {
    implicit val executionContextWithIORuntime: ExecutionContextWithIORuntime =
      ActorSystemBasedExecutionContextWithIORuntime.createFrom(system)
    import executionContextWithIORuntime.ioRuntime
    for {
      sttpBackend <- createSttpBackend()
      resolvedConfig       = config.resolved
      environment          = resolvedConfig.getString("environment")
      featureTogglesConfig = FeatureTogglesConfig.create(resolvedConfig)
      _                    = logger.info(s"Designer config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")
      countsReporter <- createCountsReporter(featureTogglesConfig, environment, sttpBackend)
      actionServiceSupplier      = new DelayedInitActionServiceSupplier
      additionalUIConfigProvider = createAdditionalUIConfigProvider(resolvedConfig, sttpBackend)
      deploymentRepository       = new DeploymentRepository(dbRef, Clock.systemDefaultZone())
      scenarioActivityRepository = DbScenarioActivityRepository.create(dbRef, designerClock)
      dbioRunner                 = DBIOActionRunner(dbRef)
      processingTypeDataProvider <- prepareProcessingTypeDataReload(
        additionalUIConfigProvider,
        actionServiceSupplier,
        scenarioActivityRepository,
        dbioRunner,
        sttpBackend,
      )

      deploymentsStatusesSynchronizer = new DeploymentsStatusesSynchronizer(
        deploymentRepository,
        processingTypeDataProvider.mapValues(
          _.deploymentData.validDeploymentManagerOrStub.deploymentSynchronisationSupport
        ),
        dbioRunner
      )
      _ <- Resource.fromAutoCloseable(
        IO {
          val scheduler = new DeploymentsStatusesSynchronizationScheduler(
            system,
            deploymentsStatusesSynchronizer,
            DeploymentsStatusesSynchronizationConfig.parse(resolvedConfig)
          )
          scheduler.start()
          scheduler
        }
      )
      statisticsPublicKey <- Resource.fromAutoCloseable(
        IO {
          Source.fromURL(getClass.getResource("/encryption.key"))
        }
      )
    } yield {
      val migrations     = processingTypeDataProvider.mapValues(_.designerModelData.modelData.migrations)
      val modelBuildInfo = processingTypeDataProvider.mapValues(_.designerModelData.modelData.buildInfo)

      implicit val implicitDbioRunner: DBIOActionRunner = dbioRunner
      val scenarioActivityRepository                    = DbScenarioActivityRepository.create(dbRef, designerClock)
      val actionRepository                              = DbScenarioActionRepository.create(dbRef, modelBuildInfo)
      val scenarioLabelsRepository                      = new ScenarioLabelsRepository(dbRef)
      val processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository, scenarioLabelsRepository)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      val futureProcessRepository =
        DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository, scenarioLabelsRepository)
      val writeProcessRepository =
        ProcessRepository.create(dbRef, designerClock, scenarioActivityRepository, scenarioLabelsRepository, migrations)

      val fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)
      val fragmentResolver   = new FragmentResolver(fragmentRepository)

      val scenarioTestServiceDeps = processingTypeDataProvider.mapValues { processingTypeData =>
        val validator = new UIProcessValidator(
          processingTypeData.name,
          ProcessValidator.default(processingTypeData.designerModelData.modelData),
          processingTypeData.deploymentData.scenarioPropertiesConfig,
          new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.name),
          new ScenarioLabelsValidator(featureTogglesConfig.scenarioLabelConfig),
          processingTypeData.deploymentData.additionalValidators,
          fragmentResolver
        )
        val substitutor =
          ProcessDictSubstitutor(processingTypeData.designerModelData.modelData.designerDictServices.dictRegistry)
        val resolver         = new UIProcessResolver(validator, substitutor)
        val scenarioResolver = new ScenarioResolver(fragmentResolver, processingTypeData.name)
        (
          validator,
          resolver,
          scenarioResolver,
          processingTypeData.designerModelData.modelData,
          processingTypeData.deploymentData.validDeploymentManagerOrStub
        )
      }

      val counter = new ProcessCounter(fragmentRepository)

      val scenarioTestService = scenarioTestServiceDeps.mapValues {
        case (_, processResolver, scenarioResolver, modelData, deploymentManager) =>
          new ScenarioTestService(
            new ModelDataTestInfoProvider(modelData),
            processResolver,
            featureTogglesConfig.testDataSettings,
            new PreliminaryScenarioTestDataSerDe(featureTogglesConfig.testDataSettings),
            counter,
            new ScenarioTestExecutorServiceImpl(scenarioResolver, deploymentManager)
          )
      }

      val processValidator = scenarioTestServiceDeps.mapValues(_._1)
      val processResolver  = scenarioTestServiceDeps.mapValues(_._2)
      val scenarioResolver = scenarioTestServiceDeps.mapValues(_._3)

      val notificationsConfig = resolvedConfig.as[NotificationConfig]("notifications")
      val processChangeListener = ProcessChangeListenerLoader.loadListeners(
        getClass.getClassLoader,
        resolvedConfig,
        NussknackerServices(new PullProcessRepository(futureProcessRepository))
      )

      val dmDispatcher =
        new DeploymentManagerDispatcher(
          processingTypeDataProvider.mapValues(_.deploymentData.validDeploymentManagerOrStub),
          futureProcessRepository
        )

      val legacyDeploymentService = new LegacyDeploymentService(
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
        processValidator,
        scenarioResolver,
        processChangeListener,
        featureTogglesConfig.scenarioStateTimeout,
        featureTogglesConfig.deploymentCommentSettings
      )
      legacyDeploymentService.invalidateInProgressActions()

      actionServiceSupplier.set(legacyDeploymentService)

      // we need to reload processing type data after deployment service creation to make sure that it will be done using
      // correct classloader and that won't cause further delays during handling requests
      processingTypeDataProvider.reloadAll().unsafeRunSync()

      val authenticationResources = AuthenticationResources(resolvedConfig, getClass.getClassLoader, sttpBackend)
      val authManager             = new AuthManager(authenticationResources)

      Initialization.init(
        migrations,
        dbRef,
        designerClock,
        processRepository,
        scenarioActivityRepository,
        scenarioLabelsRepository,
        environment
      )

      val newProcessPreparer = processingTypeDataProvider.mapValues { processingTypeData =>
        new NewProcessPreparer(
          processingTypeData.deploymentData.metaDataInitializer,
          processingTypeData.deploymentData.scenarioPropertiesConfig,
          new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.name),
        )
      }

      val stateDefinitionService = new ProcessStateDefinitionService(
        processingTypeDataProvider
          .mapValues(_.category)
          .mapCombined(_.statusNameToStateDefinitionsMapping)
      )

      val processService = new DBProcessService(
        legacyDeploymentService,
        newProcessPreparer,
        processingTypeDataProvider.mapCombined(_.parametersService),
        processResolver,
        dbioRunner,
        futureProcessRepository,
        actionRepository,
        writeProcessRepository,
      )

      val configProcessToolbarService = new ConfigScenarioToolbarService(
        CategoriesScenarioToolbarsConfigParser.parse(resolvedConfig)
      )

      def prepareAlignedComponentsDefinitionProvider(
          processingTypeData: ProcessingTypeData
      ): AlignedComponentsDefinitionProvider =
        AlignedComponentsDefinitionProvider(processingTypeData.designerModelData)

      val componentService = new DefaultComponentService(
        ComponentLinksConfigExtractor.extract(resolvedConfig),
        processingTypeDataProvider
          .mapValues { processingTypeData =>
            val alignedModelDefinitionProvider = prepareAlignedComponentsDefinitionProvider(processingTypeData)
            ComponentServiceProcessingTypeData(alignedModelDefinitionProvider, processingTypeData.category)
          },
        processService,
        fragmentRepository
      )
      val notificationService = new NotificationServiceImpl(actionRepository, dbioRunner, notificationsConfig)
      val processAuthorizer   = new AuthorizeProcess(futureProcessRepository)
      val appApiHttpService = new AppApiHttpService(
        config = resolvedConfig,
        authManager = authManager,
        processingTypeDataReloader = processingTypeDataProvider,
        modelBuildInfos = modelBuildInfo,
        categories = processingTypeDataProvider.mapValues(_.category),
        processService = processService,
        shouldExposeConfig = featureTogglesConfig.enableConfigEndpoint,
      )

      val migrationApiAdapterService = new MigrationApiAdapterService()

      val migrationService = new MigrationService(
        config = resolvedConfig,
        processService = processService,
        processResolver = processResolver,
        processAuthorizer = processAuthorizer,
        processChangeListener = processChangeListener,
        scenarioParametersService = processingTypeDataProvider.mapCombined(_.parametersService),
        useLegacyCreateScenarioApi = true,
        migrationApiAdapterService = migrationApiAdapterService
      )

      val migrationApiHttpService = new MigrationApiHttpService(
        authManager = authManager,
        migrationService = migrationService,
        migrationApiAdapterService = migrationApiAdapterService
      )
      val componentsApiHttpService = new ComponentApiHttpService(
        authManager = authManager,
        componentService = componentService
      )
      val userApiHttpService = new UserApiHttpService(
        authManager = authManager,
        categories = processingTypeDataProvider.mapValues(_.category)
      )

      val scenarioLabelsApiHttpService = new ScenarioLabelsApiHttpService(
        authManager = authManager,
        service = new ScenarioLabelsService(
          scenarioLabelsRepository,
          new ScenarioLabelsValidator(featureTogglesConfig.scenarioLabelConfig),
          dbioRunner
        )
      )

      val managementApiHttpService = new ManagementApiHttpService(
        authManager = authManager,
        dispatcher = dmDispatcher,
        processService = processService
      )

      val notificationApiHttpService = new NotificationApiHttpService(
        authManager = authManager,
        notificationService = notificationService
      )

      val nodesApiHttpService = new NodesApiHttpService(
        authManager = authManager,
        processingTypeToConfig = processingTypeDataProvider.mapValues(_.designerModelData.modelData),
        processingTypeToProcessValidator = processValidator,
        processingTypeToNodeValidator = processingTypeDataProvider.mapValues(v =>
          new NodeValidator(v.designerModelData.modelData, fragmentRepository)
        ),
        processingTypeToExpressionSuggester = processingTypeDataProvider.mapValues(v =>
          ExpressionSuggester(v.designerModelData.modelData, v.deploymentData.scenarioPropertiesConfig.keys)
        ),
        processingTypeToParametersValidator = processingTypeDataProvider.mapValues(v =>
          new ParametersValidator(v.designerModelData.modelData, v.deploymentData.scenarioPropertiesConfig.keys)
        ),
        scenarioService = processService,
      )

      val testingApiHttpService = new TestingApiHttpService(
        authManager = authManager,
        processingTypeToParametersValidator = processingTypeDataProvider.mapValues(v =>
          new ParametersValidator(v.designerModelData.modelData, v.deploymentData.scenarioPropertiesConfig.keys)
        ),
        processingTypeToScenarioTestServices = scenarioTestService,
        scenarioService = processService,
      )

      val scenarioActivityApiHttpService = new ScenarioActivityApiHttpService(
        authManager = authManager,
        deploymentManagerDispatcher = dmDispatcher,
        scenarioActivityRepository = scenarioActivityRepository,
        scenarioService = processService,
        scenarioAuthorizer = processAuthorizer,
        new ScenarioAttachmentService(
          AttachmentsConfig.create(resolvedConfig),
          scenarioActivityRepository,
          dbioRunner,
        ),
        featureTogglesConfig.deploymentCommentSettings,
        new AkkaHttpBasedTapirStreamEndpointProvider(),
        dbioRunner,
      )
      val scenarioParametersHttpService = new ScenarioParametersApiHttpService(
        authManager = authManager,
        scenarioParametersService = processingTypeDataProvider.mapCombined(_.parametersService)
      )
      val dictApiHttpService = new DictApiHttpService(
        authManager = authManager,
        processingTypeData = processingTypeDataProvider.mapValues { processingTypeData =>
          (
            processingTypeData.designerModelData.modelData.designerDictServices.dictQueryService,
            processingTypeData.designerModelData.modelData.modelDefinition.expressionConfig.dictionaries,
            processingTypeData.designerModelData.modelData.modelClassLoader.classLoader
          )
        }
      )
      val deploymentHttpService = {
        val scenarioMetadataRepository     = new ScenarioMetadataRepository(dbRef)
        val scenarioGraphVersionRepository = new ScenarioGraphVersionRepository(dbRef)
        val scenarioGraphVersionService =
          new ScenarioGraphVersionService(
            scenarioGraphVersionRepository,
            processValidator,
            scenarioResolver,
            dbioRunner
          )
        val deploymentService =
          new DeploymentService(
            scenarioMetadataRepository,
            scenarioGraphVersionService,
            deploymentRepository,
            dmDispatcher,
            dbioRunner,
            Clock.systemDefaultZone()
          )
        val activityService =
          new ActivityService(
            featureTogglesConfig.deploymentCommentSettings,
            scenarioActivityRepository,
            deploymentService,
            dbioRunner,
            designerClock,
          )
        new DeploymentApiHttpService(authManager, activityService, deploymentService)
      }

      initMetrics(metricsRegistry, resolvedConfig, futureProcessRepository)

      val apiResourcesWithAuthentication: List[RouteWithUser] = {
        val routes = List(
          new ProcessesResources(
            processService = processService,
            processStateService = legacyDeploymentService,
            processToolbarService = configProcessToolbarService,
            processAuthorizer = processAuthorizer,
            processChangeListener = processChangeListener
          ),
          new ProcessesExportResources(
            futureProcessRepository,
            processService,
            scenarioActivityRepository,
            processResolver,
            dbioRunner,
          ),
          new ManagementResources(
            processAuthorizer,
            processService,
            legacyDeploymentService,
            dmDispatcher,
            metricsRegistry,
            scenarioTestService,
            processingTypeDataProvider.mapValues(_.designerModelData.modelData)
          ),
          new ValidationResources(processService, processResolver),
          new DefinitionResources(
            processingTypeDataProvider.mapValues { processingTypeData =>
              (
                DefinitionsService(
                  processingTypeData,
                  prepareAlignedComponentsDefinitionProvider(processingTypeData),
                  new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.name),
                  fragmentRepository,
                  resolvedConfig.getAs[String]("fragmentPropertiesDocsUrl")
                )
              )
            }
          ),
          new StatusResources(stateDefinitionService),
        )

        val optionalRoutes = List(
          featureTogglesConfig.remoteEnvironment
            .map(migrationConfig =>
              new HttpRemoteEnvironment(
                migrationConfig,
                new TestModelMigrations(
                  migrations.mapValues(new ProcessModelMigrator(_)),
                  processValidator
                ),
                environment
              )
            )
            .map { remoteEnvironment =>
              new RemoteEnvironmentResources(
                remoteEnvironment,
                processService,
                processAuthorizer,
                scenarioActivityRepository,
                dbioRunner,
                designerClock,
              )
            },
          countsReporter.map(reporter =>
            new ProcessReportResources(reporter, counter, futureProcessRepository, processService)
          ),
        ).flatten
        routes ++ optionalRoutes
      }

      val usageStatisticsReportsConfig = resolvedConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")
      val fingerprintService           = new FingerprintService(new FingerprintRepositoryImpl(dbRef))
      val usageStatisticsReportsSettingsService = UsageStatisticsReportsSettingsService(
        usageStatisticsReportsConfig,
        processService,
        processingTypeDataProvider.mapValues(_.deploymentData.deploymentManagerType),
        fingerprintService,
        scenarioActivityRepository,
        componentService,
        feStatisticsRepository,
        processingTypeDataProvider
          .mapValues { processingTypeData =>
            prepareAlignedComponentsDefinitionProvider(processingTypeData)
              .getAlignedComponentsWithBuiltInComponentsAndFragments(forFragment = false, List.empty)
          }
          .all
          .values
          .flatMap(_.components)
          .toList,
        designerClock,
        dbioRunner,
      )

      val statisticUrlConfig =
        StatisticUrlConfig(publicEncryptionKey = PublicEncryptionKey(statisticsPublicKey.mkString.trim))

      val statisticsApiHttpService = new StatisticsApiHttpService(
        authManager,
        usageStatisticsReportsSettingsService,
        feStatisticsRepository,
        statisticUrlConfig
      )

      // TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
      val settingsResources = new SettingsResources(
        featureTogglesConfig,
        authenticationResources.name,
        usageStatisticsReportsConfig,
        fingerprintService
      )
      val apiResourcesWithoutAuthentication: List[Route] = List(
        settingsResources.publicRoute(),
        authenticationResources.routeWithPathPrefix,
      )

      val nuDesignerApi =
        new NuDesignerExposedApiHttpService(
          appApiHttpService,
          componentsApiHttpService,
          dictApiHttpService,
          deploymentHttpService,
          managementApiHttpService,
          migrationApiHttpService,
          nodesApiHttpService,
          testingApiHttpService,
          notificationApiHttpService,
          scenarioActivityApiHttpService,
          scenarioLabelsApiHttpService,
          scenarioParametersHttpService,
          userApiHttpService,
          statisticsApiHttpService
        )

      val akkaHttpServerInterpreter = new NuAkkaHttpServerInterpreterForTapirPurposes()

      createAppRoute(
        resolvedConfig = resolvedConfig,
        authManager = authManager,
        tapirRelatedRoutes = akkaHttpServerInterpreter.toRoute(nuDesignerApi.allEndpoints) :: Nil,
        apiResourcesWithAuthentication = apiResourcesWithAuthentication,
        apiResourcesWithoutAuthentication = apiResourcesWithoutAuthentication,
        developmentMode = featureTogglesConfig.development
      )
    }
  }

  private def createSttpBackend()(implicit executionContext: ExecutionContext) = {
    Resource
      .make(
        acquire = IO(AsyncHttpClientFutureBackend.usingConfigBuilder(identity))
      )(
        release = backend => IO.fromFuture(IO(backend.close()))
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

  private def createAppRoute(
      resolvedConfig: Config,
      authManager: AuthManager,
      tapirRelatedRoutes: List[Route],
      apiResourcesWithAuthentication: List[RouteWithUser],
      apiResourcesWithoutAuthentication: List[Route],
      developmentMode: Boolean
  ): Route = {
    // TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(resolvedConfig.getString("http.publicPath"))
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
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      actionServiceProvider: Supplier[ActionService],
      scenarioActivityRepository: ScenarioActivityRepository,
      dbioActionRunner: DBIOActionRunner,
      sttpBackend: SttpBackend[Future, Any],
  )(implicit executionContext: ExecutionContext): Resource[IO, ReloadableProcessingTypeDataProvider] = {
    Resource
      .make(
        acquire = IO(
          new ReloadableProcessingTypeDataProvider(
            processingTypeDataLoader.loadProcessingTypeData(
              getModelDependencies(additionalUIConfigProvider, _),
              getDeploymentManagerDependencies(
                actionServiceProvider,
                scenarioActivityRepository,
                dbioActionRunner,
                sttpBackend,
                _
              ),
            )
          )
        )
      )(
        release = _.close()
      )
  }

  private def getDeploymentManagerDependencies(
      actionServiceProvider: Supplier[ActionService],
      scenarioActivityRepository: ScenarioActivityRepository,
      dbioActionRunner: DBIOActionRunner,
      sttpBackend: SttpBackend[Future, Any],
      processingType: ProcessingType
  )(implicit executionContext: ExecutionContext) = {
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
      system.dispatcher,
      system,
      sttpBackend
    )
  }

  private def getModelDependencies(
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      processingType: ProcessingType
  ) = {
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
    ModelDependencies(
      additionalConfigsFromProvider,
      DesignerWideComponentId.default(processingType, _),
      workingDirectoryOpt = None, // we use the default working directory
      _ => true,
      ComponentDefinitionExtractionMode.FinalDefinition
      // todomkp load from config
    )
  }

  private def createProcessingTypeDataReload() = {}

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
