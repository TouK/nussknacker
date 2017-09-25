package pl.touk.nussknacker.ui

import java.io.File
import java.lang.Thread.UncaughtExceptionHandler

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.db.DatabaseInitializer
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpProcessMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessTypesForCategories, ProcessingTypeDeps}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.SimpleAuthenticator
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.process.report.influxdb.InfluxReporter
import slick.jdbc
import slick.jdbc.JdbcBackend

import scala.util.Try

object NussknackerApp extends App with Directives with LazyLogging {

  implicit val system = ActorSystem("nussknacker-ui")

  prepareUncaughtExceptionHandler()

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = system.settings.config
  val environment = config.getString("environment")
  val featureTogglesConfig = FeatureTogglesConfig.create(config, environment)
  val nodesConfig = Try(config.as[Map[String, SingleNodeConfig]](s"$environment.nodes")).getOrElse(Map.empty)
  logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig\nnodesConfig:$nodesConfig")

  val db: jdbc.JdbcBackend.DatabaseDef = {
    val db = JdbcBackend.Database.forConfig("db", config)
    new DatabaseInitializer(db).initDatabase()
    db
  }

  val port = args(0).toInt
  val initialProcessDirectory = new File(args(1))
  val ProcessingTypeDeps(managers, espQueryableClient, modelData) =
    ProcessingTypeDeps(config, featureTogglesConfig.standaloneMode)

  val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map {case (k, v) => (k, v.defaultValues.getOrElse(Map.empty))})
  val extractValueParameterByConfigThenType = new TypeAfterConfig(defaultParametersValues)

  val subprocessRepository = new DbSubprocessRepository(db, DefaultJdbcProfile.profile, system.dispatcher)
  val subprocessResolver = new SubprocessResolver(subprocessRepository)

  val processValidation = ProcessValidation(modelData, subprocessResolver)

  val processRepository = ProcessRepository(db, DefaultJdbcProfile.profile, processValidation, modelData)
  val deploymentProcessRepository = DeployedProcessRepository(db, DefaultJdbcProfile.profile, modelData)
  val processActivityRepository = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)
  val attachmentService = new ProcessAttachmentService(config.getString("attachmentsPath"), processActivityRepository)
  val authenticator = new SimpleAuthenticator(config.getString("usersFile"))
  val counter = new ProcessCounter(subprocessRepository)

  Initialization.init(modelData, processRepository, processActivityRepository,
    db, environment, featureTogglesConfig.development, initialProcessDirectory)
  initHttp()

  val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver)
  val jobStatusService = new JobStatusService(managementActor)

  val typesForCategories = new ProcessTypesForCategories(config)


  private val apiResources : List[RouteWithUser] = {
    val routes = List(
      new ProcessesResources(processRepository, jobStatusService, processActivityRepository, processValidation, typesForCategories),
        new ProcessesExportResources(processRepository, processActivityRepository),
        new ProcessActivityResource(processActivityRepository, attachmentService),
        ManagementResources(modelData, counter, managementActor),
        new ValidationResources(processValidation),
        new DefinitionResources(modelData, subprocessRepository, extractValueParameterByConfigThenType),
        new SignalsResources(modelData(ProcessingType.Streaming), processRepository),
        new QueryableStateResources(modelData, processRepository, espQueryableClient, jobStatusService),
        new UserResources(),
        new SettingsResources(featureTogglesConfig, nodesConfig),
        new AppResources(modelData, processRepository, jobStatusService),
        new TestInfoResources(modelData)
    )
    val optionalRoutes = List(
      featureTogglesConfig.migration
        .map(migrationConfig => new HttpProcessMigrator(migrationConfig, TestModelMigrations(modelData), environment))
        .map(migrator => new MigrationResources(migrator, processRepository)),
      featureTogglesConfig.counts
        .map(countsConfig => new InfluxReporter(environment, countsConfig))
        .map(reporter => new ProcessReportResources(reporter, counter, processRepository))
    ).flatten
    routes ++ optionalRoutes
  }

  private def initHttp() = {
    val route: Route = {

        CorsSupport.cors(featureTogglesConfig.development) {
          authenticateBasic("nussknacker", authenticator) { user =>

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

    Http().bindAndHandle(
      route,
      interface = "0.0.0.0",
      port = port
    )
  }

  //we do it, because akka creates non-daemon threads, so we have to stop ActorSystem explicitly, if initialization fails
  private def prepareUncaughtExceptionHandler() = {
    //TODO: should we set it only on main thread?
    Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error("Main thread stopped unexpectedly, terminating ActorSystem", e)
        system.shutdown()
      }
    })
  }

}