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
  EmptyAdditionalUIConfigProviderFactory
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.test.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeData}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.component.{ComponentServiceProcessingTypeData, DefaultComponentService}
import pl.touk.nussknacker.ui.config.{
  AnalyticsConfig,
  AttachmentsConfig,
  ComponentLinksConfigExtractor,
  FeatureTogglesConfig,
  UsageStatisticsReportsConfig
}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.definition.{
  DefinitionsService,
  ModelDefinitionEnricher,
  ScenarioPropertiesConfigFinalizer
}
import pl.touk.nussknacker.ui.factory.ProcessingTypeDataStateFactory
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.notifications.{NotificationConfig, NotificationServiceImpl}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReload
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{
  AuthenticationConfiguration,
  AuthenticationResources,
  LoggedUser,
  NussknackerInternalUser
}
import pl.touk.nussknacker.ui.services._
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util.{CorsSupport, OptionsMethodSupport, SecurityHeadersSupport, WithDirectives}
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}
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
    processingTypeDataStateFactory: ProcessingTypeDataStateFactory
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
      deploymentServiceSupplier  = new DelayedInitDeploymentServiceSupplier
      additionalUIConfigProvider = createAdditionalUIConfigProvider(resolvedConfig, sttpBackend)
      dbioRunner                 = DBIOActionRunner(dbRef)
      typeToConfig <- prepareProcessingTypeData(
        config,
        deploymentServiceSupplier,
        AllDeployedScenarioService(dbRef, _),
        processingTypeDataStateFactory,
        sttpBackend,
        additionalUIConfigProvider
      )
    } yield {
      val analyticsConfig = AnalyticsConfig(resolvedConfig)

      val migrations = typeToConfig.mapValues(_.modelData.migrations)
      val modelInfo  = typeToConfig.mapValues(_.modelData.modelInfo)

      val dbioRunner        = DBIOActionRunner(dbRef)
      val actionRepository  = new DbProcessActionRepository(dbRef, modelInfo)
      val processRepository = DBFetchingProcessRepository.create(dbRef, actionRepository)
      // TODO: get rid of Future based repositories - it is easier to use everywhere one implementation - DBIOAction based which allows transactions handling
      val futureProcessRepository = DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository)
      val writeProcessRepository  = ProcessRepository.create(dbRef, migrations)

      val fragmentRepository = new DefaultFragmentRepository(futureProcessRepository)
      val fragmentResolver   = new FragmentResolver(fragmentRepository)

      val scenarioTestServiceDeps = typeToConfig.mapValues { processingTypeData =>
        val validator = new UIProcessValidator(
          processingTypeData.processingType,
          ProcessValidator.default(processingTypeData.modelData),
          processingTypeData.scenarioPropertiesConfig,
          processingTypeData.additionalValidators,
          fragmentResolver
        )
        val substitutor      = ProcessDictSubstitutor(processingTypeData.modelData.designerDictServices.dictRegistry)
        val resolver         = new UIProcessResolver(validator, substitutor)
        val scenarioResolver = new ScenarioResolver(fragmentResolver, processingTypeData.processingType)
        (validator, resolver, scenarioResolver, processingTypeData.modelData, processingTypeData.deploymentManager)
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
        new DeploymentManagerDispatcher(typeToConfig.mapValues(_.deploymentManager), futureProcessRepository)

      val deploymentService = new DeploymentServiceImpl(
        dmDispatcher,
        processRepository,
        actionRepository,
        dbioRunner,
        processValidator,
        scenarioResolver,
        processChangeListener,
        featureTogglesConfig.scenarioStateTimeout
      )
      deploymentService.invalidateInProgressActions()

      deploymentServiceSupplier.set(deploymentService)

      // we need to reload processing type data after deployment service creation to make sure that it will be done using
      // correct classloader and that won't cause further delays during handling requests
      typeToConfig.reloadAll()
      val processActivityRepository = new DbProcessActivityRepository(dbRef)

      val authenticationResources = AuthenticationResources(resolvedConfig, getClass.getClassLoader, sttpBackend)

      Initialization.init(migrations, dbRef, processRepository, environment)

      val newProcessPreparer = typeToConfig.mapValues { processingTypeData =>
        new NewProcessPreparer(
          processingTypeData.metaDataInitializer,
          processingTypeData.scenarioPropertiesConfig
        )
      }

      val customActionInvokerService = new CustomActionInvokerServiceImpl(
        futureProcessRepository,
        dmDispatcher,
        deploymentService
      )
      def getProcessCategoryService() = typeToConfig.combined.categoryService

      val stateDefinitionService = new ProcessStateDefinitionService(
        typeToConfig.mapCombined(combined => (combined.statusNameToStateDefinitionsMapping, combined.categoryService)),
      )

      val processService = new DBProcessService(
        deploymentService,
        newProcessPreparer,
        getProcessCategoryService,
        processResolver,
        dbioRunner,
        futureProcessRepository,
        actionRepository,
        writeProcessRepository
      )

      val configProcessToolbarService = new ConfigProcessToolbarService(
        resolvedConfig,
        () => getProcessCategoryService().getAllCategories
      )

      def prepareModelDefinitionEnricher(processingTypeData: ProcessingTypeData): ModelDefinitionEnricher =
        ModelDefinitionEnricher(
          processingTypeData.modelData,
          processingTypeData.staticModelDefinition
        )

      val componentService = new DefaultComponentService(
        ComponentLinksConfigExtractor.extract(resolvedConfig),
        typeToConfig
          .mapValues { processingTypeData =>
            val modelDefinitionEnricher = prepareModelDefinitionEnricher(processingTypeData)
            ComponentServiceProcessingTypeData(modelDefinitionEnricher, processingTypeData.category)
          },
        processService,
        fragmentRepository
      )
      val notificationService = new NotificationServiceImpl(actionRepository, dbioRunner, notificationsConfig)
      val processAuthorizer   = new AuthorizeProcess(futureProcessRepository)
      val appApiHttpService = new AppApiHttpService(
        config = resolvedConfig,
        authenticator = authenticationResources,
        processingTypeDataReloader = typeToConfig,
        modelInfos = modelInfo,
        processService = processService,
        shouldExposeConfig = featureTogglesConfig.enableConfigEndpoint,
        getProcessCategoryService = getProcessCategoryService
      )
      val componentsApiHttpService = new ComponentApiHttpService(
        config = resolvedConfig,
        authenticator = authenticationResources,
        getProcessCategoryService = getProcessCategoryService,
        componentService = componentService
      )
      val userApiHttpService = new UserApiHttpService(
        config = resolvedConfig,
        authenticator = authenticationResources,
        getProcessCategoryService = getProcessCategoryService
      )
      val notificationApiHttpService = new NotificationApiHttpService(
        config = resolvedConfig,
        authenticator = authenticationResources,
        getProcessCategoryService = getProcessCategoryService,
        notificationService = notificationService
      )

      initMetrics(metricsRegistry, resolvedConfig, futureProcessRepository)

      val apiResourcesWithAuthentication: List[RouteWithUser] = {
        val routes = List(
          new ProcessesResources(
            processService = processService,
            deploymentService = deploymentService,
            processToolbarService = configProcessToolbarService,
            processAuthorizer = processAuthorizer,
            processChangeListener = processChangeListener
          ),
          new NodesResources(
            processService,
            typeToConfig.mapValues(_.modelData),
            processValidator,
            typeToConfig.mapValues(v => new NodeValidator(v.modelData, fragmentRepository)),
            typeToConfig.mapValues(v => ExpressionSuggester(v.modelData, v.scenarioPropertiesConfig.keys)),
            typeToConfig.mapValues(v => new ParametersValidator(v.modelData, v.scenarioPropertiesConfig.keys)),
          ),
          new ProcessesExportResources(
            futureProcessRepository,
            processService,
            processActivityRepository,
            processResolver
          ),
          new ProcessActivityResource(processActivityRepository, processService, processAuthorizer),
          new ManagementResources(
            processAuthorizer,
            processService,
            featureTogglesConfig.deploymentCommentSettings,
            deploymentService,
            dmDispatcher,
            customActionInvokerService,
            metricsRegistry,
            scenarioTestService,
            typeToConfig.mapValues(_.modelData)
          ),
          new ValidationResources(processService, processResolver),
          new DefinitionResources(
            typeToConfig.mapValues { processingTypeData =>
              DefinitionsService(
                processingTypeData,
                prepareModelDefinitionEnricher(processingTypeData),
                new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
                fragmentRepository
              )
            }
          ),
          new TestInfoResources(processAuthorizer, processService, scenarioTestService),
          new AttachmentResources(
            new ProcessAttachmentService(
              AttachmentsConfig.create(resolvedConfig),
              processActivityRepository
            ),
            processService,
            processAuthorizer
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

      val nuDesignerApi =
        new NuDesignerExposedApiHttpService(
          appApiHttpService,
          componentsApiHttpService,
          userApiHttpService,
          notificationApiHttpService
        )

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

  private def prepareProcessingTypeData(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      createAllDeployedScenarioService: ProcessingType => AllDeployedScenarioService,
      processingTypeDataStateFactory: ProcessingTypeDataStateFactory,
      sttpBackend: SttpBackend[Future, Any],
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(implicit executionContext: ExecutionContext): Resource[IO, ProcessingTypeDataReload] = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    Resource
      .make(
        acquire = IO(
          new ProcessingTypeDataReload(() =>
            processingTypeDataStateFactory.create(
              designerConfig,
              deploymentServiceSupplier,
              createAllDeployedScenarioService,
              additionalUIConfigProvider
            )
          )
        )
      )(
        release = reload =>
          IO {
            reload.all(NussknackerInternalUser.instance).values.foreach(_.close())
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
