package pl.touk.nussknacker.ui

import java.lang.Thread.UncaughtExceptionHandler

import _root_.cors.CorsSupport
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.processCounts.{CountsReporter, CountsReporterCreator}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DatabaseServer, DbConfig}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, DeployedProcessRepository, ProcessActivityRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.AuthenticatorProvider
import pl.touk.nussknacker.ui.security.ssl.{HttpsConnectionContextFactory, SslConfigParser}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.processCounts.influxdb.InfluxCountsReporterCreator
import pl.touk.nussknacker.restmodel.validation.CustomProcessValidator
import pl.touk.nussknacker.ui.definition.AdditionalProcessProperty
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

    val modelData = typeToConfig.mapValues(_.modelData)

    val typesForCategories = new ProcessTypesForCategories(config)

    val managers = typeToConfig.mapValues(_.processManager)

    val subprocessRepository = new DbSubprocessRepository(db, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val additionalFields = modelData.mapValues(_.processConfig.getOrElse[Map[String, AdditionalProcessProperty]]("additionalFieldsConfig", Map.empty))
    val customProcessNodesValidators = modelData.mapValues(CustomProcessValidator(_, config))
    val processValidation = ProcessValidation(modelData, additionalFields, subprocessResolver, customProcessNodesValidators)

    val processRepository = DBFetchingProcessRepository.create(db)
    val writeProcessRepository = WriteProcessRepository.create(db, modelData)

    val deploymentProcessRepository = DeployedProcessRepository.create(db, modelData)
    val processActivityRepository = new ProcessActivityRepository(db)
    val authenticator = AuthenticatorProvider(config, getClass.getClassLoader)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), db, environment, config.getAs[Map[String, String]]("customProcesses"))

    val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver)
    val jobStatusService = new JobStatusService(managementActor)

    val processAuthorizer = new AuthorizeProcess(processRepository)

    val apiResources: List[RouteWithUser] = {
      val routes = List(
        new ProcessesResources(
          processRepository = processRepository,
          writeRepository = writeProcessRepository,
          jobStatusService = jobStatusService,
          processActivityRepository = processActivityRepository,
          processValidation = processValidation,
          typesForCategories = typesForCategories,
          newProcessPreparer = NewProcessPreparer(typeToConfig, additionalFields),
          processAuthorizer = processAuthorizer
        ),
        new ProcessesExportResources(processRepository, processActivityRepository),
        new ProcessActivityResource(processActivityRepository, processRepository),
        ManagementResources(counter, managementActor, testResultsMaxSizeInBytes,
          processAuthorizer, processRepository, featureTogglesConfig),
        new ValidationResources(processValidation),
        new DefinitionResources(modelData, subprocessRepository),
        new SignalsResources(modelData, processRepository, processAuthorizer),
        new UserResources(typesForCategories),
        new NotificationResources(managementActor, processRepository),
        new SettingsResources(featureTogglesConfig, typeToConfig),
        new AppResources(config, modelData, processRepository, processValidation, jobStatusService),
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


    val webResources = new WebResources(config.getString("http.publicPath"))
    CorsSupport.cors(featureTogglesConfig.development) {
      authenticator { user =>
        pathPrefix("api") {
          apiResources.map(_.route(user)).reduce(_ ~ _)
        } ~
          //this is separated from api to do serve it without authentication
          pathPrefixTest(!"api") {
            webResources.route
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