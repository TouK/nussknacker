package pl.touk.nussknacker.ui

import java.lang.Thread.UncaughtExceptionHandler
import _root_.cors.CorsSupport
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.hsqldb.server.Server
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.restmodel.validation.CustomProcessValidator
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, ConfigWithDefaults, DefaultProcessToolbarsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DatabaseServer, DbConfig}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerFactory
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.processingtypedata.{BasicProcessingTypeDataReload, ProcessingTypeDataProvider, ProcessingTypeDataReader, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.security.ssl._
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}
import sttp.client.{NothingT, SttpBackend}
import sttp.client.akkahttp.AkkaHttpBackend

import scala.concurrent.Future

trait NusskanckerAppRouter extends Directives with LazyLogging {

  def create(config: Config, dbConfig: DbConfig)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable])

}

object NusskanckerDefaultAppRouter extends NusskanckerDefaultAppRouter

trait NusskanckerDefaultAppRouter extends NusskanckerAppRouter {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  //override this method to e.g. run UI with local model
  protected def prepareProcessingTypeData(config: Config): (ProcessingTypeDataProvider[ProcessingTypeData], ProcessingTypeDataReload) = {
    BasicProcessingTypeDataReload.wrapWithReloader(
      () => ProcessingTypeDataReader.loadProcessingTypeData(config)
    )
  }

  override def create(config: Config, dbConfig: DbConfig)(implicit system: ActorSystem, materializer: Materializer): (Route, Iterable[AutoCloseable]) = {
    import system.dispatcher

    implicit val sttpBackend: SttpBackend[Future, Nothing, NothingT] = AkkaHttpBackend()

    val testResultsMaxSizeInBytes = config.getOrElse[Int]("testResultsMaxSizeInBytes", 500 * 1024 * 1000)
    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config)
    logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")

    val (typeToConfig, reload) = prepareProcessingTypeData(config)

    val analyticsConfig = AnalyticsConfig(config)
    val defaultProcessToolbarsConfig = DefaultProcessToolbarsConfig.create(config)

    val modelData = typeToConfig.mapValues(_.modelData)

    val typesForCategories = new ProcessTypesForCategories(config)

    val managers = typeToConfig.mapValues(_.processManager)

    val subprocessRepository = new DbSubprocessRepository(dbConfig, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val additionalProperties = modelData.mapValues(_.processConfig.getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty))
    val customProcessNodesValidators = modelData.mapValues(CustomProcessValidator(_, config))
    val processValidation = ProcessValidation(modelData, additionalProperties, subprocessResolver, customProcessNodesValidators)

    val substitutorsByProcessType = modelData.mapValues(modelData => ProcessDictSubstitutor(modelData.dictServices.dictRegistry))
    val processResolving = new UIProcessResolving(processValidation, substitutorsByProcessType)

    val dbRepositoryManager = RepositoryManager.createDbRepositoryManager(dbConfig)
    val processRepository = DBFetchingProcessRepository.create(dbConfig)
    val writeProcessRepository = ProcessRepository.create(dbConfig, modelData)

    val actionRepository = DbProcessActionRepository.create(dbConfig, modelData)
    val processActivityRepository = new ProcessActivityRepository(dbConfig)

    val authenticator = AuthenticatorProvider(config, getClass.getClassLoader, typesForCategories.getAllCategories)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), dbConfig, environment, config.getAs[Map[String, String]]("customProcesses"))

    val processChangeListener = ProcessChangeListenerFactory.serviceLoader(getClass.getClassLoader).create(
      config,
      NussknackerServices(new PullProcessRepository(processRepository))
    )

    val newProcessPreparer = NewProcessPreparer(typeToConfig, additionalProperties)

    val systemRequestTimeout = system.settings.config.getDuration("akka.http.server.request-timeout")
    val managementActor = system.actorOf(ManagementActor.props(managers, processRepository, actionRepository, subprocessResolver, processChangeListener), "management")
    val processService = new DBProcessService(managementActor, systemRequestTimeout, newProcessPreparer, typesForCategories, processResolving, dbRepositoryManager, processRepository, actionRepository, writeProcessRepository)

    val processAuthorizer = new AuthorizeProcess(processRepository)
    val appResources = new AppResources(config, reload, modelData, processRepository, processValidation, processService)

    val countsReporter = featureTogglesConfig.counts.map(prepareCountsReporter(environment, _))

    val apiResourcesWithAuthentication: List[RouteWithUser] = {
      val routes = List(
        new ProcessesResources(
          processRepository = processRepository,
          processService = processService,
          processValidation = processValidation,
          processResolving = processResolving,
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener,
          typeToConfig = typeToConfig
        ),
        new ProcessesExportResources(processRepository, processActivityRepository, processResolving),
        new ProcessActivityResource(processActivityRepository, processRepository),
        ManagementResources(counter, managementActor, testResultsMaxSizeInBytes,
          processAuthorizer, processRepository, featureTogglesConfig, processResolving, processService),
        new ValidationResources(processResolving),
        new DefinitionResources(modelData, typeToConfig, subprocessRepository, typesForCategories),
        new SignalsResources(modelData, processRepository, processAuthorizer),
        new UserResources(typesForCategories),
        new NotificationResources(managementActor, processRepository),
        appResources,
        TestInfoResources(modelData, processAuthorizer, processRepository),
        new ServiceRoutes(modelData)
      )

      val optionalRoutes = List(
        featureTogglesConfig.remoteEnvironment
          .map(migrationConfig => new HttpRemoteEnvironment(migrationConfig, new TestModelMigrations(modelData.mapValues(_.migrations), processValidation), environment))
          .map(remoteEnvironment => new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer)),
        countsReporter
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository)),
        featureTogglesConfig.attachments
          .map(path => new ProcessAttachmentService(path, processActivityRepository))
          .map(service => new AttachmentResources(service, processRepository)),
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
      new SettingsResources(featureTogglesConfig, defaultProcessToolbarsConfig, authenticator.config, typeToConfig, analyticsConfig).publicRoute(),
      appResources.publicRoute()
    ) ++ authenticator.routes

    //TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(config.getString("http.publicPath"))
    val route = CorsSupport.cors(featureTogglesConfig.development) {
      pathPrefixTest(!"api") {
        webResources.route
      } ~  pathPrefix("api") {
        apiResourcesWithoutAuthentication.reduce(_ ~ _)
      } ~ authenticator.directive { user =>
        pathPrefix("api") {
          apiResourcesWithAuthentication.map(_.securedRoute(user)).reduce(_ ~ _)
        }
      }
    }

    (route, typeToConfig.all.values ++ countsReporter.toList)
  }

  //by default, we use InfluxCountsReporterCreator
  private def prepareCountsReporter(env: String, config: Config): CountsReporter = {
    val configAtKey = config.atKey(CountsReporterCreator.reporterCreatorConfigPath)
    val creator = Multiplicity(ScalaServiceLoader.load[CountsReporterCreator](getClass.getClassLoader)) match {
      case One(cr) =>
        cr
      case Empty() =>
        new InfluxCountsReporterCreator
      case Many(many) =>
        throw new IllegalArgumentException(s"Many CountsReporters found: ${many.mkString(", ")}")
    }

    creator.createReporter(env, configAtKey)
  }
}

object NussknackerAppInitializer extends NussknackerAppInitializer(ConfigWithDefaults(ConfigFactory.load()))

class NussknackerAppInitializer(baseConfig: Config) extends LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  protected val config: Config = ConfigWithDefaults(baseConfig)

  protected implicit val system: ActorSystem = ActorSystem("nussknacker-ui", config)
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()

  protected val jdbcServerConfig: Option[DatabaseServer.Config] = config.getAs[DatabaseServer.Config]("jdbcServer")
  protected val hsqlServer: Option[Server] = jdbcServerConfig.map(DatabaseServer(_))

  // TODO: switch to general configuration via application.conf when https://github.com/akka/akka-http/issues/55 will be ready
  val interface: String = config.getString("http.interface")
  val port: Int = config.getInt("http.port")

  def init(router: NusskanckerAppRouter): (Route, Iterable[AutoCloseable]) = {
    val db = initDb(config)

    val (route, objectsToClose) = router.create(config, db)

    prepareUncaughtExceptionHandler(objectsToClose)
    Runtime.getRuntime.addShutdownHook(new ShutdownHandler(objectsToClose))

    SslConfigParser.sslEnabled(config) match {
      case Some(keyStoreConfig) =>
        bindHttps(interface, port, HttpsConnectionContextFactory.createContext(keyStoreConfig), route)
      case None =>
        bindHttp(interface, port, route)
    }

    (route, objectsToClose)
  }
  
  def bindHttp(interface: String, port: Int, route: Route)(implicit system: ActorSystem, materializer: Materializer): Future[Http.ServerBinding] = {
    Http().bindAndHandle(
      handler = route,
      interface = interface,
      port = port
    )
  }

  def bindHttps(interface: String, port: Int, httpsContext: HttpsConnectionContext, route: Route)(implicit system: ActorSystem, materializer: Materializer): Future[Http.ServerBinding] = {
    Http().bindAndHandle(
      handler = route,
      interface = interface,
      port = port,
      connectionContext = httpsContext
    )
  }

  def initDb(config: Config): DbConfig = {
    // Default true because of back compatibility
    if (jdbcServerConfig.exists(_.enabled.getOrElse(true))) {
      hsqlServer.foreach(_.start())
    }

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
    hsqlServer.foreach(_.shutdown())
    system.terminate()
  }
}

object NussknackerApp extends App {
  new NussknackerAppInitializer(ConfigFactory.load()).init(NusskanckerDefaultAppRouter)
}
