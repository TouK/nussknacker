package pl.touk.nussknacker.ui

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.metrics.core.HttpMetrics._
import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.dropwizard.{DropwizardRegistry, DropwizardSettings}
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.component.DefaultComponentService
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, AttachmentsConfig, FeatureTogglesConfig, UiConfigLoader, UsageStatisticsReportsConfig}
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.notifications.{NotificationConfig, NotificationService, NotificationsListener}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor.ActorBasedManagementService
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.processingtypedata._
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.security.ssl._
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettings
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util.{CorsSupport, OptionsMethodSupport, SecurityHeadersSupport, WithDirectives}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}
import sttp.client3.SttpBackend
import sttp.client3.akkahttp.AkkaHttpBackend

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait NusskanckerAppRouter extends Directives with LazyLogging {

  def create(config: Config, dbConfig: DbConfig, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable])

}

object NusskanckerDefaultAppRouter extends NusskanckerDefaultAppRouter

trait NusskanckerDefaultAppRouter extends NusskanckerAppRouter {

  import net.ceedubs.ficus.Ficus._

  //override this method to e.g. run UI with local model
  protected def prepareProcessingTypeData(config: Config, getDeploymentService: () => DeploymentServiceImpl, categoriesService: ProcessCategoryService)
                                         (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                          sttpBackend: SttpBackend[Future, Any]): (ProcessingTypeDataProvider[ProcessingTypeData], ProcessingTypeDataReload with Initialization) = {
    BasicProcessingTypeDataReload.wrapWithReloader(
      () => {
        implicit val deploymentService: DeploymentServiceImpl = getDeploymentService()
        implicit val categoriesServiceImp: ProcessCategoryService = categoriesService
        ProcessingTypeDataReader.loadProcessingTypeData(config)
      }
    )
  }

  def initMetrics(metricsRegistry: MetricRegistry,
                  config: Config,
                  processRepository: DBFetchingProcessRepository[Future] with BasicRepository): Unit = {
    new RepositoryGauges(metricsRegistry, config.getDuration("repositoryGaugesCacheDuration"), processRepository).prepareGauges()
  }

  override def create(config: Config, dbConfig: DbConfig, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable]) = {
    import system.dispatcher

    implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(system)

    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config)
    logger.info(s"Designer config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")

    // TODO: this ugly hack is because we have cycle in dependencies: deploymentService -> repostories -> modelData -> typeToConfig -> deploymentService
    // We should figure out how to split ModelData to be not passed to repositories
    var deploymentService: DeploymentServiceImpl = null
    val getDeploymentService: () => DeploymentServiceImpl = () => {
      assert(deploymentService != null, "Illegal initialization: DeploymentService should be initialized before ProcessingTypeData")
      deploymentService
    }

    val processCategoryService: ProcessCategoryService = new ConfigProcessCategoryService(config)

    val (typeToConfig, reload) = prepareProcessingTypeData(config, getDeploymentService, processCategoryService)

    val analyticsConfig = AnalyticsConfig(config)

    val modelData = typeToConfig.mapValues(_.modelData)

    val managers = typeToConfig.mapValues(_.deploymentManager)

    val subprocessRepository = new DbSubprocessRepository(dbConfig, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val additionalProperties = typeToConfig.mapValues(_.additionalPropertiesConfig)
    val processValidation = ProcessValidation(modelData, additionalProperties, typeToConfig.mapValues(_.additionalValidators), subprocessResolver)

    val substitutorsByProcessType = modelData.mapValues(modelData => ProcessDictSubstitutor(modelData.dictServices.dictRegistry))
    val processResolving = new UIProcessResolving(processValidation, substitutorsByProcessType)

    val dbRepositoryManager = RepositoryManager.createDbRepositoryManager(dbConfig)
    val processRepository = DBFetchingProcessRepository.create(dbConfig)
    val writeProcessRepository = ProcessRepository.create(dbConfig, modelData)

    val notificationListener = new NotificationsListener(config.as[NotificationConfig]("notifications"), processRepository.fetchProcessName(_))
    val processChangeListener = ProcessChangeListenerLoader
      .loadListeners(getClass.getClassLoader, config, NussknackerServices(new PullProcessRepository(processRepository)), notificationListener)

    val scenarioResolver = new ScenarioResolver(subprocessResolver)
    val actionRepository = DbProcessActionRepository.create(dbConfig, modelData)
    deploymentService = new DeploymentServiceImpl(managers.forTypeUnsafe, processRepository, actionRepository, scenarioResolver, processChangeListener)
    reload.init() // we need to init processing type data after deployment service creation to make sure that it will be done using correct classloader and that won't cause further delays during handling requests
    val processActivityRepository = new DbProcessActivityRepository(dbConfig)

    val authenticationResources = AuthenticationResources(config, getClass.getClassLoader)
    val authorizationRules = AuthenticationConfiguration.getRules(config)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), dbConfig, environment)

    val newProcessPreparer = NewProcessPreparer(typeToConfig, additionalProperties)

    val systemRequestTimeout = Timeout(system.settings.config.getDuration("akka.http.server.request-timeout").toMillis, TimeUnit.MILLISECONDS)

    val dmDispatcher = new DeploymentManagerDispatcher(managers, processRepository)
    val processStateService = new ProcessStateServiceImpl(processRepository, dmDispatcher, deploymentService)
    val customActionInvokerService = new CustomActionInvokerServiceImpl(processRepository, dmDispatcher, processStateService)
    val testExecutorService = new ScenarioTestExecutorServiceImpl(scenarioResolver, dmDispatcher)
    val managementActor = system.actorOf(ManagementActor.props(dmDispatcher, deploymentService, customActionInvokerService, processStateService, testExecutorService), "management")
    val managementService = new ActorBasedManagementService(managementActor, systemRequestTimeout)
    val processService = new DBProcessService(managementService, newProcessPreparer,
      processCategoryService, processResolving, dbRepositoryManager, processRepository, actionRepository,
      writeProcessRepository, processValidation
    )
    val scenarioTestService = ScenarioTestService(modelData, featureTogglesConfig.testDataSettings,
      processResolving, counter, managementService)

    val configProcessToolbarService = new ConfigProcessToolbarService(config, processCategoryService.getAllCategories)

    val processAuthorizer = new AuthorizeProcess(processRepository)
    val appResources = new AppResources(
      config = config,
      processingTypeDataReload = reload,
      modelData = modelData,
      processRepository = processRepository,
      processValidation = processValidation,
      processService = processService,
      exposeConfig = featureTogglesConfig.enableConfigEndpoint,
      processCategoryService = processCategoryService
    )

    val countsReporter = featureTogglesConfig.counts.flatMap(prepareCountsReporter(environment, _))

    val componentService = DefaultComponentService(config, typeToConfig, processService, processCategoryService)

    val notificationService = new NotificationService(managementService, notificationListener)

    initMetrics(metricsRegistry, config, processRepository)

    val apiResourcesWithAuthentication: List[RouteWithUser] = {
      val routes = List(
        new ProcessesResources(
          processRepository = processRepository,
          processService = processService,
          processToolbarService = configProcessToolbarService,
          processResolving = processResolving,
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener,
          typeToConfig = typeToConfig
        ),
        new NodesResources(processRepository, subprocessRepository, typeToConfig.mapValues(_.modelData), processValidation),
        new ProcessesExportResources(processRepository, processActivityRepository, processResolving),
        new ProcessActivityResource(processActivityRepository, processRepository, processAuthorizer),
        ManagementResources(processAuthorizer, processRepository, featureTogglesConfig, processService, dmDispatcher, metricsRegistry, scenarioTestService),
        new ValidationResources(processRepository ,processResolving),
        new DefinitionResources(modelData, typeToConfig, subprocessRepository, processCategoryService),
        new UserResources(processCategoryService),
        new NotificationResources(notificationService),
        appResources,
        new TestInfoResources(processAuthorizer, processRepository, scenarioTestService),
        new ComponentResource(componentService),
        new AttachmentResources(new ProcessAttachmentService(AttachmentsConfig.create(config), processActivityRepository), processRepository, processAuthorizer)
      )

      val optionalRoutes = List(
        featureTogglesConfig.remoteEnvironment
          .map(migrationConfig => new HttpRemoteEnvironment(migrationConfig, new TestModelMigrations(modelData.mapValues(_.migrations), processValidation), environment))
          .map(remoteEnvironment => new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer)),
        countsReporter
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository))
      ).flatten
      routes ++ optionalRoutes
    }

    val usageStatisticsReportsConfig = config.as[UsageStatisticsReportsConfig]("usageStatisticsReports")
    val usageStatisticsReportsSettings = UsageStatisticsReportsSettings.prepare(usageStatisticsReportsConfig, typeToConfig.mapValues(_.usageStatistics))

    //TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
    val apiResourcesWithoutAuthentication: List[Route] = List(
      new SettingsResources(featureTogglesConfig, authenticationResources.name, analyticsConfig, usageStatisticsReportsSettings).publicRoute(),
      appResources.publicRoute(),
      authenticationResources.routeWithPathPrefix,
    )

    //TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(config.getString("http.publicPath"))
    val route = WithDirectives(CorsSupport.cors(featureTogglesConfig.development), SecurityHeadersSupport(), OptionsMethodSupport()) {
      pathPrefixTest(!"api") {
        webResources.route
      } ~ pathPrefix("api") {
        apiResourcesWithoutAuthentication.reduce(_ ~ _)
      } ~ authenticationResources.authenticate() { authenticatedUser =>
        pathPrefix("api") {
          authorize(authenticatedUser.roles.nonEmpty) {
            val loggedUser = LoggedUser(authenticatedUser, authorizationRules, processCategoryService.getAllCategories)
            apiResourcesWithAuthentication.map(_.securedRoute(loggedUser)).reduce(_ ~ _)
          }
        }
      }
    }

    val sttpClosable: AutoCloseable = () => sttpBackend.close()
    (route, typeToConfig.all.values.toList ++ countsReporter.toList :+ sttpClosable)
  }

  //by default, we use InfluxCountsReporterCreator
  private def prepareCountsReporter(env: String, config: Config)
                                   (implicit backend: SttpBackend[Future, Any]): Option[CountsReporter[Future]] = {
    val configAtKey = config.atKey(CountsReporterCreator.reporterCreatorConfigPath)
    val creator = Multiplicity(ScalaServiceLoader.load[CountsReporterCreator](getClass.getClassLoader)) match {
      case One(cr) =>
        cr
      case Empty() =>
        new InfluxCountsReporterCreator
      case Many(many) =>
        throw new IllegalArgumentException(s"Many CountsReporters found: ${many.mkString(", ")}")
    }

    Try(Option(creator.createReporter(env, configAtKey))).recover {
      case NonFatal(ex) =>
        logger.warn(s"Error while setting up counts mechanism: ${ex.getMessage}. Counts mechanism will be disabled.")
        None
    }.get
  }
}

object NussknackerAppInitializer extends NussknackerAppInitializer(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader))

class NussknackerAppInitializer(baseUnresolvedConfig: Config) extends LazyLogging {

  import net.ceedubs.ficus.Ficus._

  protected val config: Config = UiConfigLoader.load(baseUnresolvedConfig, getClass.getClassLoader)

  protected implicit val system: ActorSystem = ActorSystem("nussknacker-designer", config)
  protected implicit val materializer: Materializer = Materializer(system)

  val interface: String = config.getString("http.interface")
  val port: Int = config.getInt("http.port")

  def init(router: NusskanckerAppRouter): (Route, Iterable[AutoCloseable]) = {
    JavaClassVersionChecker.check()
    SLF4JBridgeHandlerRegistrar.register()
    //we prepare temporary ExceptionHandler to shutdown actorSystem in case of exception during creation
    prepareUncaughtExceptionHandler(Nil)

    val db = initDb(config)
    val metricsRegistry = new MetricRegistry

    val (route, objectsToClose) = router.create(config, db, metricsRegistry)

    prepareUncaughtExceptionHandler(objectsToClose)
    Runtime.getRuntime.addShutdownHook(new ShutdownHandler(objectsToClose))

    //JmxReporter does not allocate resources, safe to close
    JmxReporter.forRegistry(metricsRegistry).build().start()


    val bindingResultF = SslConfigParser.sslEnabled(config) match {
      case Some(keyStoreConfig) =>
        bindHttps(interface, port, HttpsConnectionContextFactory.createServerContext(keyStoreConfig), route, metricsRegistry)
      case None =>
        bindHttp(interface, port, route, metricsRegistry)
    }
    bindingResultF.foreach { bindingResult =>
      logger.info(s"Nussknacker designer started on ${interface}:${bindingResult.localAddress.getPort}")
    }(ExecutionContext.global)

    (route, objectsToClose)
  }

  def bindHttp(interface: String, port: Int, route: Route, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): Future[Http.ServerBinding] = {
    Http().newMeteredServerAt(
      interface = interface,
      port = port,
      prepareHttpMetricRegistry(metricsRegistry)
    ).bind(route)
  }

  def bindHttps(interface: String, port: Int, httpsContext: HttpsConnectionContext, route: Route, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): Future[Http.ServerBinding] = {
    Http().newMeteredServerAt(
      interface = interface,
      port = port,
      prepareHttpMetricRegistry(metricsRegistry)
    ).enableHttps(httpsContext).bind(route)
  }

  private def prepareHttpMetricRegistry(metricsRegistry: MetricRegistry): HttpMetricsRegistry = {
    val settings: HttpMetricsSettings = DropwizardSettings.default
    new DropwizardRegistry(settings)(metricsRegistry)
  }

  def initDb(config: Config): DbConfig = {
    val db = JdbcBackend.Database.forConfig("db", config)
    val profile = chooseDbProfile(config)
    val dbConfig = DbConfig(db, profile)
    DatabaseInitializer.initDatabase("db", config)

    dbConfig
  }

  private def chooseDbProfile(config: Config): JdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    config.getAs[String]("db.url") match {
      case Some(jdbcUrlPattern("postgresql")) => PostgresProfile
      case Some(jdbcUrlPattern("hsqldb")) => HsqldbProfile
      case None => HsqldbProfile
      case _ => throw new IllegalStateException("unsupported jdbc url")
    }
  }

  //we do it, because akka creates non-daemon threads, so we have to stop ActorSystem explicitly, if initialization fails
  private def prepareUncaughtExceptionHandler(objectsToClose: Iterable[AutoCloseable]): Unit = {
    //TODO: should we set it only on main thread?
    Thread.currentThread().setUncaughtExceptionHandler((t: Thread, e: Throwable) => {
      logger.error("Main thread stopped unexpectedly, terminating ActorSystem", e)
      closeAndShutdownAll(objectsToClose)
    })
  }

  class ShutdownHandler(objectsToClose: Iterable[AutoCloseable]) extends Thread {
    override def run(): Unit = {
      logger.info("Stopping application")
      closeAndShutdownAll(objectsToClose)
    }
  }

  private def closeAndShutdownAll(objectsToClose: Iterable[AutoCloseable]): Unit = {
    objectsToClose.foreach(_.close())
    system.terminate()
  }
}

object NussknackerApp extends App {
  NussknackerAppInitializer.init(NusskanckerDefaultAppRouter)
}
