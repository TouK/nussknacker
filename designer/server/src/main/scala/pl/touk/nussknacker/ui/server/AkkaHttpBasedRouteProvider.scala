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
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  AdditionalUIConfigProviderFactory,
  DesignerWideComponentId,
  EmptyAdditionalUIConfigProviderFactory
}
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeployedScenariosProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
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
  AnalyticsConfig,
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
import pl.touk.nussknacker.ui.factory.ProcessingTypeDataStateFactory
import pl.touk.nussknacker.ui.initialization.Initialization
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
  ScenarioResolver,
  ScenarioTestExecutorServiceImpl
}
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentRepository, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeData, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.process.version.{ScenarioGraphVersionRepository, ScenarioGraphVersionService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.services.{ManagementApiHttpService, NuDesignerExposedApiHttpService}
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepositoryImpl
import pl.touk.nussknacker.ui.statistics.{FingerprintService, UsageStatisticsReportsSettingsService}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util.{CorsSupport, OptionsMethodSupport, SecurityHeadersSupport, WithDirectives}
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class AkkaHttpBasedRouteProvider(
    dbRef: DbRef,
    metricsRegistry: MetricRegistry,
    processingTypeDataStateFactory: ProcessingTypeDataStateFactory,
    feStatisticsRepository: FEStatisticsRepository[Future]
)(implicit system: ActorSystem, materializer: Materializer)
    extends RouteProvider[Route]
    with Directives
    with LazyLogging {

  override def createRoute(config: ConfigWithUnresolvedVersion): Resource[IO, Route] = {
    import system.dispatcher
    for {
      sttpBackend <- createSttpBackend()
      resolvedConfig       = config.resolved
      environment          = resolvedConfig.getString("environment")
      featureTogglesConfig = FeatureTogglesConfig.create(resolvedConfig)
      _                    = logger.info(s"Designer config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")
      countsReporter <- createCountsReporter(featureTogglesConfig, environment, sttpBackend)
      actionServiceSupplier      = new DelayedInitActionServiceSupplier
      additionalUIConfigProvider = createAdditionalUIConfigProvider(resolvedConfig, sttpBackend)
      processingTypeDataProvider <- prepareProcessingTypeDataReload(
        config,
        processingTypeDataStateFactory,
        additionalUIConfigProvider,
        actionServiceSupplier,
        DefaultProcessingTypeDeployedScenariosProvider(dbRef, _),
        sttpBackend,
      )
    } yield {
      val analyticsConfig = AnalyticsConfig(resolvedConfig)

      val migrations     = processingTypeDataProvider.mapValues(_.designerModelData.modelData.migrations)
      val modelBuildInfo = processingTypeDataProvider.mapValues(_.designerModelData.modelData.buildInfo)

      implicit val dbioRunner: DBIOActionRunner = DBIOActionRunner(dbRef)
      val actionRepository                      = new DbProcessActionRepository(dbRef, modelBuildInfo)
      val processRepository                     = DBFetchingProcessRepository.create(dbRef, actionRepository)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      val futureProcessRepository = DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository)
      val writeProcessRepository  = ProcessRepository.create(dbRef, migrations)

      val fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)
      val fragmentResolver   = new FragmentResolver(fragmentRepository)

      val scenarioTestServiceDeps = processingTypeDataProvider.mapValues { processingTypeData =>
        val validator = new UIProcessValidator(
          processingTypeData.name,
          ProcessValidator.default(processingTypeData.designerModelData.modelData),
          processingTypeData.deploymentData.scenarioPropertiesConfig,
          new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.name),
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
      processingTypeDataProvider.reloadAll()
      val processActivityRepository = new DbProcessActivityRepository(dbRef)

      val authenticationResources = AuthenticationResources(resolvedConfig, getClass.getClassLoader, sttpBackend)

      Initialization.init(migrations, dbRef, processRepository, environment)

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
        writeProcessRepository
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
        authenticator = authenticationResources,
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
        authenticator = authenticationResources,
        migrationService = migrationService,
        migrationApiAdapterService = migrationApiAdapterService
      )
      val componentsApiHttpService = new ComponentApiHttpService(
        authenticator = authenticationResources,
        componentService = componentService
      )
      val userApiHttpService = new UserApiHttpService(
        authenticator = authenticationResources,
        categories = processingTypeDataProvider.mapValues(_.category)
      )

      val managementApiHttpService = new ManagementApiHttpService(
        authenticator = authenticationResources,
        dispatcher = dmDispatcher,
        processService = processService
      )

      val notificationApiHttpService = new NotificationApiHttpService(
        authenticator = authenticationResources,
        notificationService = notificationService
      )

      val nodesApiHttpService = new NodesApiHttpService(
        authenticator = authenticationResources,
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
        scenarioService = processService
      )

      val scenarioActivityApiHttpService = new ScenarioActivityApiHttpService(
        authenticator = authenticationResources,
        scenarioActivityRepository = processActivityRepository,
        scenarioService = processService,
        scenarioAuthorizer = processAuthorizer,
        new ScenarioAttachmentService(
          AttachmentsConfig.create(resolvedConfig),
          processActivityRepository
        ),
        new AkkaHttpBasedTapirStreamEndpointProvider()
      )
      val scenarioParametersHttpService = new ScenarioParametersApiHttpService(
        authenticator = authenticationResources,
        scenarioParametersService = processingTypeDataProvider.mapCombined(_.parametersService)
      )
      val dictApiHttpService = new DictApiHttpService(
        authenticator = authenticationResources,
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
          new ScenarioGraphVersionService(scenarioGraphVersionRepository, processValidator, scenarioResolver)
        val deploymentRepository = new DeploymentRepository(dbRef)
        val deploymentService =
          new DeploymentService(
            scenarioMetadataRepository,
            scenarioGraphVersionService,
            deploymentRepository,
            dmDispatcher,
            dbioRunner,
            Clock.systemDefaultZone()
          )
        new DeploymentApiHttpService(authenticationResources, deploymentService)
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
            processActivityRepository,
            processResolver
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
                  fragmentRepository
                )
              )
            }
          ),
          new TestInfoResources(processAuthorizer, processService, scenarioTestService),
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
                processAuthorizer
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
        processActivityRepository,
        componentService,
        feStatisticsRepository
      )

      val statisticsApiHttpService = new StatisticsApiHttpService(
        authenticationResources,
        usageStatisticsReportsSettingsService,
        feStatisticsRepository
      )

      // TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
      val settingsResources = new SettingsResources(
        featureTogglesConfig,
        authenticationResources.name,
        analyticsConfig
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
          notificationApiHttpService,
          scenarioActivityApiHttpService,
          scenarioParametersHttpService,
          userApiHttpService,
          statisticsApiHttpService
        )

      val akkaHttpServerInterpreter = {
        import system.dispatcher
        new NuAkkaHttpServerInterpreterForTapirPurposes()
      }

      createAppRoute(
        resolvedConfig = resolvedConfig,
        authenticationResources = authenticationResources,
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
      authenticationResources: AuthenticationResources,
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
          authenticationResources.authenticate() { authenticatedUser =>
            authorize(authenticatedUser.roles.nonEmpty) {
              val loggedUser = LoggedUser(
                authenticatedUser = authenticatedUser,
                rules = authenticationResources.configuration.rules
              )
              apiResourcesWithAuthentication.map(_.securedRouteWithErrorHandling(loggedUser)).reduce(_ ~ _)
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
      designerConfig: ConfigWithUnresolvedVersion,
      processingTypeDataStateFactory: ProcessingTypeDataStateFactory,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      actionServiceProvider: Supplier[ActionService],
      createDeployedScenariosProvider: ProcessingType => ProcessingTypeDeployedScenariosProvider,
      sttpBackend: SttpBackend[Future, Any],
  ): Resource[IO, ProcessingTypeDataReload] = {
    Resource
      .make(
        acquire = IO(
          new ProcessingTypeDataReload({ () =>
            def getDeploymentManagerDependencies(processingType: ProcessingType) = {
              DeploymentManagerDependencies(
                createDeployedScenariosProvider(processingType),
                new DefaultProcessingTypeActionService(
                  processingType,
                  actionServiceProvider.get(),
                ),
                system.dispatcher,
                system,
                sttpBackend
              )
            }
            def getModelDependencies(processingType: ProcessingType) = {
              val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)
              ModelDependencies(
                additionalConfigsFromProvider,
                DesignerWideComponentId.default(processingType, _),
                workingDirectoryOpt = None, // we use the default working directory
                _ => true
              )
            }
            processingTypeDataStateFactory.create(
              designerConfig,
              getModelDependencies,
              getDeploymentManagerDependencies,
            )
          })
        )
      )(
        release = reload =>
          IO {
            reload
              .all(NussknackerInternalUser.instance)
              .values
              .foreach(_.close())
          }
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
