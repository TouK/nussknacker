package pl.touk.nussknacker.ui

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
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
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, AttachmentsConfig, FeatureTogglesConfig, UiConfigLoader}
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.notifications.{ManagementActorCurrentDeployments, NotificationConfig, NotificationService, NotificationsListener}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService, ManagementActor, ScenarioResolver}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.processingtypedata._
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.security.ssl._
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util.{CorsSupport, OptionsMethodSupport, SecurityHeadersSupport, WithDirectives}
import pl.touk.nussknacker.ui.validation.{CustomProcessValidatorLoader, ProcessValidation}
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.client.{NothingT, SttpBackend}

import java.lang.Thread.UncaughtExceptionHandler
import scala.collection.JavaConverters.getClass
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait NusskanckerAppRouter extends Directives with LazyLogging {

  def create(config: Config, dbConfig: DbConfig, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable])

}

object NusskanckerDefaultAppRouter extends NusskanckerDefaultAppRouter

trait NusskanckerDefaultAppRouter extends NusskanckerAppRouter {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  //override this method to e.g. run UI with local model
  protected def prepareProcessingTypeData(config: Config, getDeploymentService: () => DeploymentService, categoriesService: ProcessCategoryService)
                                         (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                          sttpBackend: SttpBackend[Future, Nothing, NothingT]): (ProcessingTypeDataProvider[ProcessingTypeData], ProcessingTypeDataReload with Initialization) = {
    BasicProcessingTypeDataReload.wrapWithReloader(
      () => {
        implicit val deploymentService: DeploymentService = getDeploymentService()
        implicit val categoriesServiceImp: ProcessCategoryService = categoriesService
        ProcessingTypeDataReader.loadProcessingTypeData(config)
      }
    )
  }

  def initMetrics(metricsRegistry: MetricRegistry, processRepository: DBFetchingProcessRepository[Future] with BasicRepository): Unit = {
    new RepositoryGauges(metricsRegistry, processRepository).prepareGauges()
  }

  override def create(config: Config, dbConfig: DbConfig, metricsRegistry: MetricRegistry)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable]) = {
    import system.dispatcher

    implicit val sttpBackend: SttpBackend[Future, Nothing, NothingT] = AkkaHttpBackend.usingActorSystem(system)

    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config)
    logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")

    // TODO: this ugly hack is because we have cycle in dependencies: deploymentService -> repostories -> modelData -> typeToConfig -> deploymentService
    // We should figure out how to split ModelData to be not passed to repositories
    var deploymentService: DeploymentService = null
    val getDeploymentService: () => DeploymentService = () => {
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
    val customProcessNodesValidators = modelData.mapValues(CustomProcessValidatorLoader.loadProcessValidators(_, config))
    val processValidation = ProcessValidation(modelData, additionalProperties, subprocessResolver, customProcessNodesValidators)

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
    deploymentService = new DeploymentService(processRepository, actionRepository, scenarioResolver, processChangeListener)
    reload.init() // we need to init processing type data after deployment service creation to make sure that it will be done using correct classloader and that won't cause further delays during handling requests
    val processActivityRepository = new DbProcessActivityRepository(dbConfig)

    val authenticationResources = AuthenticationResources(config, getClass.getClassLoader)
    val authorizationRules = AuthenticationConfiguration.getRules(config)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), dbConfig, environment)

    val newProcessPreparer = NewProcessPreparer(typeToConfig, additionalProperties)

    val systemRequestTimeout = system.settings.config.getDuration("akka.http.server.request-timeout")
    val managementActor = system.actorOf(ManagementActor.props(managers, processRepository, scenarioResolver, deploymentService), "management")
    val processService = new DBProcessService(managementActor, systemRequestTimeout, newProcessPreparer,
      processCategoryService, processResolving, dbRepositoryManager, processRepository, actionRepository,
      writeProcessRepository
    )

    val configProcessToolbarService = new ConfigProcessToolbarService(config, processCategoryService.getAllCategories)

    val processAuthorizer = new AuthorizeProcess(processRepository)
    val appResources = new AppResources(config, reload, modelData, processRepository, processValidation, processService, exposeConfig = featureTogglesConfig.enableConfigEndpoint)

    val countsReporter = featureTogglesConfig.counts.flatMap(prepareCountsReporter(environment, _))

    val componentService = DefaultComponentService(config, typeToConfig, processService, processCategoryService)

    val notificationService = new NotificationService(new ManagementActorCurrentDeployments(managementActor), notificationListener)

    initMetrics(metricsRegistry, processRepository)

    val apiResourcesWithAuthentication: List[RouteWithUser] = {
      val routes = List(
        new ProcessesResources(
          processRepository = processRepository,
          subprocessRepository = subprocessRepository,
          processService = processService,
          processToolbarService = configProcessToolbarService,
          processResolving = processResolving,
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener,
          typeToConfig = typeToConfig
        ),
        new ProcessesExportResources(processRepository, processActivityRepository, processResolving),
        new ProcessActivityResource(processActivityRepository, processRepository, processAuthorizer),
        ManagementResources(counter, managementActor, processAuthorizer, processRepository, featureTogglesConfig, processResolving, processService, metricsRegistry),
        new ValidationResources(processRepository ,processResolving),
        new DefinitionResources(modelData, typeToConfig, subprocessRepository, processCategoryService),
        new SignalsResources(modelData, processRepository, processAuthorizer),
        new UserResources(processCategoryService),
        new NotificationResources(notificationService),
        appResources,
        TestInfoResources(modelData, processAuthorizer, processRepository, featureTogglesConfig),
        new ServiceRoutes(modelData),
        new ComponentResource(componentService),
        new AttachmentResources(new ProcessAttachmentService(AttachmentsConfig.create(config), processActivityRepository), processRepository, processAuthorizer)
      )

      val optionalRoutes = List(
        featureTogglesConfig.remoteEnvironment
          .map(migrationConfig => new HttpRemoteEnvironment(migrationConfig, new TestModelMigrations(modelData.mapValues(_.migrations), processValidation), environment))
          .map(remoteEnvironment => new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer)),
        countsReporter
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository)),
        Some(new QueryableStateResources(
          typeToConfig = typeToConfig,
          processRepository = processRepository,
          processService = processService,
          processAuthorizer = processAuthorizer
        ))
      ).flatten
      routes ++ optionalRoutes
    }

    //TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
    val apiResourcesWithoutAuthentication: List[Route] = List(
      new SettingsResources(featureTogglesConfig, authenticationResources.name, analyticsConfig).publicRoute(),
      appResources.publicRoute(),
      authenticationResources.routeWithPathPrefix
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
                                   (implicit backend: SttpBackend[Future, Nothing, NothingT]): Option[CountsReporter[Future]] = {
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

  protected implicit val system: ActorSystem = ActorSystem("nussknacker-ui", config)
  protected implicit val materializer: Materializer = Materializer(system)

  val interface: String = config.getString("http.interface")
  val port: Int = config.getInt("http.port")

  def init(router: NusskanckerAppRouter): (Route, Iterable[AutoCloseable]) = {
    JavaClassVersionChecker.check()
    SLF4JBridgeHandlerRegistrar.register()

    val db = initDb(config)
    val metricsRegistry = new MetricRegistry

    val (route, objectsToClose) = router.create(config, db, metricsRegistry)

    prepareUncaughtExceptionHandler(objectsToClose)
    Runtime.getRuntime.addShutdownHook(new ShutdownHandler(objectsToClose))


    //JmxReporter does not allocate resources, safe to close
    JmxReporter.forRegistry(metricsRegistry).build().start()


    SslConfigParser.sslEnabled(config) match {
      case Some(keyStoreConfig) =>
        bindHttps(interface, port, HttpsConnectionContextFactory.createServerContext(keyStoreConfig), route, metricsRegistry)
      case None =>
        bindHttp(interface, port, route, metricsRegistry)
    }

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
    }
  }

  //we do it, because akka creates non-daemon threads, so we have to stop ActorSystem explicitly, if initialization fails
  private def prepareUncaughtExceptionHandler(objectsToClose: Iterable[AutoCloseable]): Unit = {
    //TODO: should we set it only on main thread?
    Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error("Main thread stopped unexpectedly, terminating ActorSystem", e)
        closeAndShutdownAll(objectsToClose)
      }
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
