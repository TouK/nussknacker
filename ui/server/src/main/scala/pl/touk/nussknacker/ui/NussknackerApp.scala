package pl.touk.nussknacker.ui

import java.lang.Thread.UncaughtExceptionHandler

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DatabaseServer, DbConfig}
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, DeployedProcessRepository, ProcessActivityRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.process.uiconfig.defaults._
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.AuthenticatorProvider
import pl.touk.nussknacker.ui.security.ssl.{HttpsConnectionContextFactory, SslConfigParser}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.process.report.influxdb.InfluxReporter
import slick.jdbc.JdbcBackend

object NussknackerApp extends App with Directives with LazyLogging {

  private implicit val system = ActorSystem("nussknacker-ui")
  private implicit val materializer = ActorMaterializer()

  prepareUncaughtExceptionHandler()

  //TODO: pass port as part of config

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

  def initializeRoute(config: Config)(implicit system: ActorSystem, materializer: Materializer) : Route = {
    import system.dispatcher

    val testResultsMaxSizeInBytes = config.getOrElse[Int]("testResultsMaxSizeInBytes",  500 * 1024 * 1000)
    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config)
    //TODO clean up config and make it more typed
    //TODO add nodesConfig support for standalone mode
    val nodesConfig = config.getOrElse[Map[String, SingleNodeConfig]]("processConfig.nodes", Map.empty)
    logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig\nnodesConfig:$nodesConfig")

    val nodeCategoryMapping = config.getOrElse[Map[String, String]]("processConfig.nodeCategoryMapping", Map.empty)

    val db: DbConfig = {
      val db = JdbcBackend.Database.forConfig("db", config)
      new DatabaseInitializer(db).initDatabase()
      DbConfig(db, DefaultJdbcProfile.profile)
    }

    val ProcessingTypeDeps(managers, modelData) = ProcessingTypeDeps(config, featureTogglesConfig.standaloneMode)

    val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map {case (k, v) => (k, v.defaultValues.getOrElse(Map.empty))})
    val extractValueParameterByConfigThenType = DefaultValueExtractorChain(defaultParametersValues)

    val subprocessRepository = new DbSubprocessRepository(db, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val processValidation = ProcessValidation(modelData, subprocessResolver)

    val processRepository = DBFetchingProcessRepository.create(db)
    val writeProcessRepository = WriteProcessRepository.create(db, modelData)

    val deploymentProcessRepository = DeployedProcessRepository.create(db, modelData)
    val processActivityRepository = new ProcessActivityRepository(db)
    val authenticator =  AuthenticatorProvider(config, getClass.getClassLoader)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), db, environment, config.getAs[Map[String, String]]("customProcesses"))

    val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver)
    val jobStatusService = new JobStatusService(managementActor)

    val typesForCategories = new ProcessTypesForCategories(config)
    val newProcessPreparer = new NewProcessPreparer(modelData.mapValues(_.processDefinition))

    val processAuthorizer = new AuthorizeProcess(processRepository)

    val apiResources : List[RouteWithUser] = {
      val routes = List(
          new ProcessesResources(
            repository = processRepository,
            writeRepository = writeProcessRepository,
            jobStatusService = jobStatusService,
            processActivityRepository = processActivityRepository,
            processValidation = processValidation,
            typesForCategories = typesForCategories,
            newProcessPreparer = newProcessPreparer,
            processAuthorizer = processAuthorizer
          ),
          new ProcessesExportResources(processRepository, processActivityRepository),
          new ProcessActivityResource(processActivityRepository),
          ManagementResources(counter, managementActor, testResultsMaxSizeInBytes, processAuthorizer),
          new ValidationResources(processValidation),
          new DefinitionResources(modelData, subprocessRepository, extractValueParameterByConfigThenType, nodesConfig, nodeCategoryMapping),
          new SignalsResources(modelData(ProcessingType.Streaming), processRepository, processAuthorizer),
          new UserResources(),
          new SettingsResources(featureTogglesConfig, nodesConfig),
          new AppResources(config, modelData, processRepository, processValidation, jobStatusService),
          TestInfoResources(modelData,processAuthorizer),
        new ServiceRoutes(modelData)
      )
      val optionalRoutes = List(
        featureTogglesConfig.remoteEnvironment
          .map(migrationConfig => new HttpRemoteEnvironment(migrationConfig, TestModelMigrations(modelData), environment))
          .map(remoteEnvironment => new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer)),
        featureTogglesConfig.counts
          .map(countsConfig => new InfluxReporter(environment, countsConfig))
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository)),
        featureTogglesConfig.attachments
          .map(path => new ProcessAttachmentService(path, processActivityRepository))
          .map(service => new AttachmentResources(service)),
        featureTogglesConfig.queryableStateProxyUrl
          .map(url => new QueryableStateResources(
            processDefinition = modelData,
            processRepository = processRepository,
            queryableClient = EspQueryableClient(url),
            jobStatusService = jobStatusService,
            processAuthorizer = processAuthorizer
          ))
      ).flatten
      routes ++ optionalRoutes
    }

    CorsSupport.cors(featureTogglesConfig.development) {
      authenticator { user =>
        pathPrefix("api") {
          apiResources.map(_.route(user)).reduce(_ ~ _)
        } ~
          //this is separated from api to do serve it without authentication
          pathPrefixTest(!"api") {
            WebResources.route
          }
        }
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