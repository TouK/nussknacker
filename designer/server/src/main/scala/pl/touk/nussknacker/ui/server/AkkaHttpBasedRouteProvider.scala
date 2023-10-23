package pl.touk.nussknacker.ui.server

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import cats.effect.{ContextShift, IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProviderFactory,
  EmptyAdditionalUIConfigProviderFactory
}
import pl.touk.nussknacker.engine.api.fixedvaluespresets.{
  DefaultFixedValuesPresetProviderFactory,
  FixedValuesPresetProviderFactory
}
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, ProcessingTypeData}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.component.DefaultComponentService
import pl.touk.nussknacker.ui.config.{
  AnalyticsConfig,
  AttachmentsConfig,
  ComponentLinksConfigExtractor,
  FeatureTogglesConfig,
  UsageStatisticsReportsConfig
}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.factory.ProcessingTypeDataProviderFactory
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.notifications.{NotificationConfig, NotificationServiceImpl}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.fragment.{DbFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.processingtypedata.{
  BasicProcessingTypeDataReload,
  Initialization,
  ProcessingTypeDataProvider,
  ProcessingTypeDataReload
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{AuthenticationConfiguration, AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.services.{AppApiHttpService, NuDesignerExposedApiHttpService}
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util.{CorsSupport, OptionsMethodSupport, SecurityHeadersSupport, WithDirectives}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class AkkaHttpBasedRouteProvider(
    dbRef: DbRef,
    metricsRegistry: MetricRegistry,
    processingTypeDataProviderFactory: ProcessingTypeDataProviderFactory
)(implicit system: ActorSystem, materializer: Materializer)
    extends RouteProvider[Route]
    with Directives
    with LazyLogging {

  private val akkaHttpServerInterpreter = {
    import system.dispatcher
    new NuAkkaHttpServerInterpreterForTapirPurposes()
  }

  override def createRoute(config: ConfigWithUnresolvedVersion): Resource[IO, Route] = {
    import system.dispatcher
    for {
      sttpBackend <- createSttpBackend()
      resolvedConfig       = config.resolved
      environment          = resolvedConfig.getString("environment")
      featureTogglesConfig = FeatureTogglesConfig.create(resolvedConfig)
      _                    = logger.info(s"Designer config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")
      countsReporter <- createCountsReporter(featureTogglesConfig, environment, sttpBackend)
      deploymentServiceSupplier = new DelayedInitDeploymentServiceSupplier
      typeToConfigAndReload <- prepareProcessingTypeData(
        config,
        deploymentServiceSupplier,
        processingTypeDataProviderFactory,
        sttpBackend
      )
      (typeToConfig, reload) = typeToConfigAndReload
    } yield {
      val analyticsConfig = AnalyticsConfig(resolvedConfig)

      val modelData = typeToConfig.mapValues(_.modelData)

      val managers = typeToConfig.mapValues(_.deploymentManager)

      val fragmentRepository = new DbFragmentRepository(dbRef, system.dispatcher)
      val fragmentResolver   = new FragmentResolver(fragmentRepository)

      val scenarioProperties = typeToConfig.mapValues(_.scenarioPropertiesConfig)
      val processValidation = ProcessValidation(
        modelData,
        scenarioProperties,
        typeToConfig.mapValues(_.additionalValidators),
        fragmentResolver
      )

      val substitutorsByProcessType =
        modelData.mapValues(modelData => ProcessDictSubstitutor(modelData.uiDictServices.dictRegistry))
      val processResolving = new UIProcessResolving(processValidation, substitutorsByProcessType)

      val dbioRunner        = DBIOActionRunner(dbRef)
      val actionRepository  = DbProcessActionRepository.create(dbRef, modelData)
      val processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      val futureProcessRepository = DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository)
      val writeProcessRepository  = ProcessRepository.create(dbRef, modelData)

      val notificationsConfig = resolvedConfig.as[NotificationConfig]("notifications")
      val processChangeListener = ProcessChangeListenerLoader.loadListeners(
        getClass.getClassLoader,
        resolvedConfig,
        NussknackerServices(new PullProcessRepository(futureProcessRepository))
      )

      val scenarioResolver = new ScenarioResolver(fragmentResolver)
      val dmDispatcher     = new DeploymentManagerDispatcher(managers, futureProcessRepository)

      val fixedValuesPresetProvider = createFixedValuePresetProvider(resolvedConfig, sttpBackend)

      val deploymentService = new DeploymentServiceImpl(
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
        processValidation,
        scenarioResolver,
        processChangeListener,
        featureTogglesConfig.scenarioStateTimeout,
        fixedValuesPresetProvider
      )
      deploymentService.invalidateInProgressActions()

      deploymentServiceSupplier.set(deploymentService)

      // we need to init processing type data after deployment service creation to make sure that it will be done using
      // correct classloader and that won't cause further delays during handling requests
      reload.init()
      val processActivityRepository = new DbProcessActivityRepository(dbRef)

      val authenticationResources = AuthenticationResources(resolvedConfig, getClass.getClassLoader, sttpBackend)

      val counter = new ProcessCounter(fragmentRepository)

      Initialization.init(modelData.mapValues(_.migrations), dbRef, processRepository, environment)

      val newProcessPreparer = NewProcessPreparer(typeToConfig, scenarioProperties)

      val customActionInvokerService = new CustomActionInvokerServiceImpl(
        futureProcessRepository,
        dmDispatcher,
        deploymentService
      )
      val testExecutorService         = new ScenarioTestExecutorServiceImpl(scenarioResolver, dmDispatcher)
      def getProcessCategoryService() = typeToConfig.combined.categoryService

      val stateDefinitionService = new ProcessStateDefinitionService(
        typeToConfig.mapCombined(combined => (combined.statusNameToStateDefinitionsMapping, combined.categoryService)),
      )

      val processService = new DBProcessService(
        deploymentService,
        newProcessPreparer,
        getProcessCategoryService,
        processResolving,
        dbioRunner,
        futureProcessRepository,
        actionRepository,
        writeProcessRepository,
        processValidation,
        fixedValuesPresetProvider
      )
      val scenarioTestService = ScenarioTestService(
        modelData,
        featureTogglesConfig.testDataSettings,
        processResolving,
        counter,
        testExecutorService
      )

      val configProcessToolbarService = new ConfigProcessToolbarService(
        resolvedConfig,
        () => getProcessCategoryService().getAllCategories
      )

      val processAuthorizer = new AuthorizeProcess(futureProcessRepository)
      val appApiHttpService = new AppApiHttpService(
        config = resolvedConfig,
        authenticator = authenticationResources,
        processingTypeDataReloader = reload,
        modelData = modelData,
        processRepository = futureProcessRepository,
        processValidation = processValidation,
        deploymentService = deploymentService,
        shouldExposeConfig = featureTogglesConfig.enableConfigEndpoint,
        getProcessCategoryService = getProcessCategoryService
      )

      val additionalUIConfigProvider = createAdditionalUIConfigProvider(resolvedConfig, sttpBackend)

      val componentService = DefaultComponentService(
        ComponentLinksConfigExtractor.extract(resolvedConfig),
        typeToConfig.mapCombined(combined => (combined.componentIdProvider, combined.categoryService)),
        processService,
        additionalUIConfigProvider,
        fixedValuesPresetProvider
      )

      val notificationService = new NotificationServiceImpl(actionRepository, dbioRunner, notificationsConfig)

      initMetrics(metricsRegistry, resolvedConfig, futureProcessRepository)

      val apiResourcesWithAuthentication: List[RouteWithUser] = {
        val routes = List(
          new ProcessesResources(
            processRepository = futureProcessRepository,
            processService = processService,
            deploymentService = deploymentService,
            processToolbarService = configProcessToolbarService,
            processResolving = processResolving,
            processAuthorizer = processAuthorizer,
            processChangeListener = processChangeListener
          ),
          new NodesResources(
            futureProcessRepository,
            fragmentRepository,
            typeToConfig.mapValues(_.modelData),
            processValidation,
            typeToConfig.mapValues(v => ExpressionSuggester(v.modelData)),
            fixedValuesPresetProvider
          ),
          new ProcessesExportResources(futureProcessRepository, processActivityRepository, processResolving),
          new ProcessActivityResource(processActivityRepository, futureProcessRepository, processAuthorizer),
          new ManagementResources(
            processAuthorizer,
            futureProcessRepository,
            featureTogglesConfig.deploymentCommentSettings,
            deploymentService,
            dmDispatcher,
            customActionInvokerService,
            metricsRegistry,
            scenarioTestService,
            typeToConfig.mapValues(_.modelData)
          ),
          new ValidationResources(futureProcessRepository, processResolving),
          new DefinitionResources(
            modelData,
            typeToConfig,
            fragmentRepository,
            getProcessCategoryService,
            additionalUIConfigProvider,
            fixedValuesPresetProvider
          ),
          new UserResources(getProcessCategoryService),
          new NotificationResources(notificationService),
          new TestInfoResources(processAuthorizer, futureProcessRepository, scenarioTestService),
          new ComponentResource(componentService),
          new AttachmentResources(
            new ProcessAttachmentService(
              AttachmentsConfig.create(resolvedConfig),
              processActivityRepository
            ),
            futureProcessRepository,
            processAuthorizer
          ),
          new StatusResources(stateDefinitionService),
        )

        val optionalRoutes = List(
          featureTogglesConfig.remoteEnvironment
            .map(migrationConfig =>
              new HttpRemoteEnvironment(
                migrationConfig,
                new TestModelMigrations(modelData.mapValues(_.migrations), processValidation),
                environment
              )
            )
            .map { remoteEnvironment =>
              new RemoteEnvironmentResources(remoteEnvironment, futureProcessRepository, processAuthorizer)
            },
          countsReporter.map(reporter => new ProcessReportResources(reporter, counter, futureProcessRepository)),
        ).flatten
        routes ++ optionalRoutes
      }

      val usageStatisticsReportsConfig = resolvedConfig.as[UsageStatisticsReportsConfig]("usageStatisticsReports")
      val usageStatisticsReportsSettingsDeterminer = UsageStatisticsReportsSettingsDeterminer(
        usageStatisticsReportsConfig,
        typeToConfig.mapValues(_.usageStatistics)
      )

      // TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
      val settingsResources = new SettingsResources(
        featureTogglesConfig,
        authenticationResources.name,
        analyticsConfig,
        usageStatisticsReportsSettingsDeterminer.determineSettings()
      )
      val apiResourcesWithoutAuthentication: List[Route] = List(
        settingsResources.publicRoute(),
        authenticationResources.routeWithPathPrefix,
      )

      val nuDesignerApi = new NuDesignerExposedApiHttpService(appApiHttpService)

      createAppRoute(
        resolvedConfig = resolvedConfig,
        authenticationResources = authenticationResources,
        tapirRelatedRoutes = akkaHttpServerInterpreter.toRoute(nuDesignerApi.allEndpoints) :: Nil,
        apiResourcesWithAuthentication = apiResourcesWithAuthentication,
        apiResourcesWithoutAuthentication = apiResourcesWithoutAuthentication,
        getProcessCategoryService = getProcessCategoryService,
        developmentMode = featureTogglesConfig.development
      )
    }
  }

  private def createSttpBackend()(implicit executionContext: ExecutionContext) = {
    implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
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
      getProcessCategoryService: () => ProcessCategoryService,
      developmentMode: Boolean
  )(implicit executionContext: ExecutionContext): Route = {
    // TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(resolvedConfig.getString("http.publicPath"))
    WithDirectives(CorsSupport.cors(developmentMode), SecurityHeadersSupport(), OptionsMethodSupport()) {
      tapirRelatedRoutes.reduce(_ ~ _) ~
        pathPrefixTest(!"api") {
          webResources.route
        } ~ pathPrefix("api") {
          apiResourcesWithoutAuthentication.reduce(_ ~ _)
        } ~ authenticationResources.authenticate() { authenticatedUser =>
          pathPrefix("api") {
            authorize(authenticatedUser.roles.nonEmpty) {
              val loggedUser = LoggedUser(
                authenticatedUser = authenticatedUser,
                rules = AuthenticationConfiguration.getRules(resolvedConfig),
                processCategories = getProcessCategoryService().getAllCategories
              )
              apiResourcesWithAuthentication.map(_.securedRoute(loggedUser)).reduce(_ ~ _)
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

  private def prepareProcessingTypeData(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      processingTypeDataProviderFactory: ProcessingTypeDataProviderFactory,
      sttpBackend: SttpBackend[Future, Any]
  )(implicit executionContext: ExecutionContext): Resource[
    IO,
    (
        ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData],
        ProcessingTypeDataReload with Initialization
    )
  ] = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    Resource
      .make(
        acquire = IO(
          BasicProcessingTypeDataReload.wrapWithReloader(() =>
            processingTypeDataProviderFactory.create(designerConfig, deploymentServiceSupplier)
          )
        )
      )(
        release = provider =>
          IO {
            val (processingTypeDataProvider, _) = provider
            processingTypeDataProvider.all.values.foreach(_.close())
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

  private def createFixedValuePresetProvider(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ) = {
    val fixedValuesPresetProviderFactory: FixedValuesPresetProviderFactory = {
      Multiplicity(
        ScalaServiceLoader.load[FixedValuesPresetProviderFactory](getClass.getClassLoader)
      ) match {
        case Empty()              => new DefaultFixedValuesPresetProviderFactory
        case One(providerFactory) => providerFactory
        case Many(moreThanOne) =>
          throw new IllegalArgumentException(
            s"More than one FixedValuesPresetProviderFactory instance found: $moreThanOne"
          )
      }
    }

    fixedValuesPresetProviderFactory.create(config, sttpBackend)
  }

  private class DelayedInitDeploymentServiceSupplier extends Supplier[DeploymentService] {
    private val deploymentServiceRef = new AtomicReference[Option[DeploymentService]](None)

    override def get(): DeploymentService = {
      val deploymentService = deploymentServiceRef.get()
      deploymentService.getOrElse(
        throw new IllegalStateException(
          "Illegal initialization: DeploymentService should be initialized before ProcessingTypeData"
        )
      )
    }

    def set(deploymentService: DeploymentService): Unit = deploymentServiceRef.set(Some(deploymentService))
  }

}
