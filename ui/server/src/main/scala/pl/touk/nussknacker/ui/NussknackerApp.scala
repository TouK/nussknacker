package pl.touk.nussknacker.ui

import java.lang.Thread.UncaughtExceptionHandler

import _root_.cors.CorsSupport
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.restmodel.validation.CustomProcessValidator
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig, ProcessStateConfig}
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DatabaseServer, DbConfig}
import pl.touk.nussknacker.ui.definition.AdditionalProcessProperty
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.listener.ProcessChangeListenerFactory
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, DeployedProcessRepository, ProcessActivityRepository, PullProcessRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.security.ssl._
import pl.touk.nussknacker.ui.listener.services.NussknackerServices
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend, PostgresProfile}


object NussknackerApp extends App with Directives with LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  private implicit val system = ActorSystem("nussknacker-ui")
  private implicit val materializer = ActorMaterializer()

  prepareUncaughtExceptionHandler()

  private val config = system.settings.config.withFallback(ConfigFactory.load("defaultConfig.conf"))

  private val hsqlServer = config.getAs[DatabaseServer.Config]("jdbcServer")
    .map(DatabaseServer(_))
  hsqlServer.foreach(_.start())

  private val route = initializeRoute(config)

  // TODO: switch to general configuration via application.conf when https://github.com/akka/akka-http/issues/55 will be ready
  val port = config.getInt("http.port")
  val interface = config.getString("http.interface")
  SslConfigParser.sslEnabled(config) match {
    case Some(keyStoreConfig) =>
      val httpsContext = HttpsConnectionContextFactory.createContext(keyStoreConfig)
      bindHttps(interface, port, httpsContext, route)
    case None =>
      bindHttp(interface, port, route)
  }

  def bindHttp(interface: String, port: Int, route: Route)(implicit system: ActorSystem, materializer: Materializer) = {
    Http().bindAndHandle(
      handler = route,
      interface = interface,
      port = port
    )
  }

  def bindHttps(interface: String, port: Int, httpsContext: HttpsConnectionContext, route: Route)(implicit system: ActorSystem, materializer: Materializer) = {
    Http().bindAndHandle(
      handler = route,
      interface = interface,
      port = port,
      connectionContext = httpsContext
    )
  }

  def initializeRoute(config: Config)(implicit system: ActorSystem, materializer: Materializer): Route = {
    import system.dispatcher

    val testResultsMaxSizeInBytes = config.getOrElse[Int]("testResultsMaxSizeInBytes", 500 * 1024 * 1000)
    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config)
    logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig")

    val db = initDb(config)

    val typeToConfig = ProcessingTypeDeps(config, featureTogglesConfig.standaloneMode)

    val analyticsConfig = AnalyticsConfig(config)

    val modelData = typeToConfig.mapValues(_.modelData)

    val typesForCategories = new ProcessTypesForCategories(config)

    val managers = typeToConfig.mapValues(_.processManager)

    val subprocessRepository = new DbSubprocessRepository(db, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val additionalFields = modelData.mapValues(_.processConfig.getOrElse[Map[String, AdditionalProcessProperty]]("additionalFieldsConfig", Map.empty))
    val customProcessNodesValidators = modelData.mapValues(CustomProcessValidator(_, config))
    val processValidation = ProcessValidation(modelData, additionalFields, subprocessResolver, customProcessNodesValidators)

    val substitutorsByProcessType = modelData.mapValues(modelData => ProcessDictSubstitutor(modelData.dictServices.dictRegistry))
    val processResolving = new UIProcessResolving(processValidation, substitutorsByProcessType)

    val processRepository = DBFetchingProcessRepository.create(db)
    val writeProcessRepository = WriteProcessRepository.create(db, modelData)

    val deploymentProcessRepository = DeployedProcessRepository.create(db, modelData)
    val processActivityRepository = new ProcessActivityRepository(db)

    val authenticator = AuthenticatorProvider(config, getClass.getClassLoader, typesForCategories.getAllCategories)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), db, environment, config.getAs[Map[String, String]]("customProcesses"))

    val processChangeListener = ProcessChangeListenerFactory.serviceLoader(getClass.getClassLoader).create(
      config,
      NussknackerServices(new PullProcessRepository(processRepository))
    )

    val managementActor = system.actorOf(
      ManagementActor.props(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver, processChangeListener), "management")
    val jobStatusService = new JobStatusService(managementActor)

    val processAuthorizer = new AuthorizeProcess(processRepository)
    val appResources = new AppResources(config, modelData, processRepository, processValidation, jobStatusService)

    val apiResourcesWithAuthentication: List[RouteWithUser] = {
      val routes = List(
        new ProcessesResources(
          processRepository = processRepository,
          writeRepository = writeProcessRepository,
          jobStatusService = jobStatusService,
          processActivityRepository = processActivityRepository,
          processValidation = processValidation,
          processResolving = processResolving,
          typesForCategories = typesForCategories,
          newProcessPreparer = NewProcessPreparer(typeToConfig, additionalFields),
          processAuthorizer = processAuthorizer,
          processChangeListener = processChangeListener
        ),
        new ProcessesExportResources(processRepository, processActivityRepository, processResolving),
        new ProcessActivityResource(processActivityRepository, processRepository),
        ManagementResources(counter, managementActor, testResultsMaxSizeInBytes,
          processAuthorizer, processRepository, featureTogglesConfig, processResolving),
        new ValidationResources(processResolving),
        new DefinitionResources(modelData, subprocessRepository, typesForCategories),
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
        featureTogglesConfig.counts
          .map(prepareCountsReporter(environment, _))
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository)),
        featureTogglesConfig.attachments
          .map(path => new ProcessAttachmentService(path, processActivityRepository))
          .map(service => new AttachmentResources(service, processRepository)),
        Some(new QueryableStateResources(
          typeToConfig = typeToConfig,
          processRepository = processRepository,
          jobStatusService = jobStatusService,
          processAuthorizer = processAuthorizer
        ))
      ).flatten
      routes ++ optionalRoutes
    }

    //TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
    val apiResourcesWithoutAuthentication: List[Route] = List(
      new SettingsResources(featureTogglesConfig, typeToConfig, authenticator.config, analyticsConfig).publicRoute(),
      appResources.publicRoute()
    ) ++ authenticator.routes

    //TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(config.getString("http.publicPath"))
    CorsSupport.cors(featureTogglesConfig.development) {
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

  private def initDb(config: Config) = {
    val db = JdbcBackend.Database.forConfig("db", config)
    val profile = chooseDbProfile(config)
    val dbConfig = DbConfig(db, profile)
    new DatabaseInitializer(dbConfig).initDatabase()
    dbConfig
  }

  private def chooseDbProfile(config: Config) = {
    config.getOrElse[String]("db.type", "hsql") match {
      case "hsql" => HsqldbProfile
      case "postgres" => PostgresProfile
    }
  }

  //we do it, because akka creates non-daemon threads, so we have to stop ActorSystem explicitly, if initialization fails
  private def prepareUncaughtExceptionHandler() = {
    //TODO: should we set it only on main thread?
    Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error("Main thread stopped unexpectedly, terminating ActorSystem", e)
        hsqlServer.foreach(_.shutdown())
        system.terminate()
      }
    })
  }
}