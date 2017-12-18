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
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, TestModelMigrations}
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, DeployedProcessRepository, ProcessActivityRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.process.report.influxdb.InfluxReporter
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.security.AuthenticatorProvider
import slick.jdbc.JdbcBackend

import scala.util.Try

object NussknackerApp extends App with Directives with LazyLogging {

  implicit val system = ActorSystem("nussknacker-ui")

  prepareUncaughtExceptionHandler()

  implicit val materializer = ActorMaterializer()

  val initialProcessDirectory = new File(args(1))
  val port = args(0).toInt


  Http().bindAndHandle(
    initializeRoute(initialProcessDirectory),
    interface = "0.0.0.0",
    port = port
  )

  def initializeRoute(initialProcessDirectory: File)(implicit system: ActorSystem, materializer: ActorMaterializer) : Route = {

    import system.dispatcher

    val config = system.settings.config
    val environment = config.getString("environment")
    val featureTogglesConfig = FeatureTogglesConfig.create(config, environment)
    //TODO clean up config and make it more typed
    //TODO add nodesConfig support for standalone mode
    val nodesConfig = Try(config.as[Map[String, SingleNodeConfig]]("processConfig.nodes")).getOrElse(Map.empty)
    logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig\nnodesConfig:$nodesConfig")

    val db: DbConfig = {
      val db = JdbcBackend.Database.forConfig("db", config)
      new DatabaseInitializer(db).initDatabase()
      DbConfig(db, DefaultJdbcProfile.profile)
    }

    val ProcessingTypeDeps(managers, espQueryableClient, modelData) =
      ProcessingTypeDeps(config, featureTogglesConfig.standaloneMode)

    val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map {case (k, v) => (k, v.defaultValues.getOrElse(Map.empty))})
    val extractValueParameterByConfigThenType = new TypeAfterConfig(defaultParametersValues)

    val subprocessRepository = new DbSubprocessRepository(db, system.dispatcher)
    val subprocessResolver = new SubprocessResolver(subprocessRepository)

    val processValidation = ProcessValidation(modelData, subprocessResolver)

    val processRepository = DBFetchingProcessRepository.create(db)
    val writeProcessRepository = WriteProcessRepository.create(db, modelData)

    val deploymentProcessRepository = DeployedProcessRepository.create(db, modelData)
    val processActivityRepository = new ProcessActivityRepository(db)
    val attachmentService = new ProcessAttachmentService(config.getString("attachmentsPath"), processActivityRepository)
    val authenticator =  AuthenticatorProvider(config, getClass.getClassLoader)

    val counter = new ProcessCounter(subprocessRepository)

    Initialization.init(modelData.mapValues(_.migrations), db, environment, initialProcessDirectory)

    val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver)
    val jobStatusService = new JobStatusService(managementActor)

    val typesForCategories = new ProcessTypesForCategories(config)
    val newProcessPreparer = new NewProcessPreparer(modelData.mapValues(_.processDefinition))

    val apiResources : List[RouteWithUser] = {
      val routes = List(
          new ProcessesResources(processRepository, writeProcessRepository, jobStatusService,
          processActivityRepository, processValidation, typesForCategories, newProcessPreparer),
          new ProcessesExportResources(processRepository, processActivityRepository),
          new ProcessActivityResource(processActivityRepository, attachmentService),
          ManagementResources(modelData, counter, managementActor),
          new ValidationResources(processValidation),
          new DefinitionResources(modelData, subprocessRepository, extractValueParameterByConfigThenType),
          new SignalsResources(modelData(ProcessingType.Streaming), processRepository),
          new QueryableStateResources(modelData, processRepository, espQueryableClient, jobStatusService),
          new UserResources(),
          new SettingsResources(featureTogglesConfig, nodesConfig),
          new AppResources(modelData, processRepository, processValidation, jobStatusService),
          new TestInfoResources(modelData),
        new ServiceRoutes(modelData)
      )
      val optionalRoutes = List(
        featureTogglesConfig.remoteEnvironment
          .map(migrationConfig => new HttpRemoteEnvironment(migrationConfig, TestModelMigrations(modelData), environment))
          .map(remoteEnvironment => new RemoteEnvironmentResources(remoteEnvironment, processRepository)),
        featureTogglesConfig.counts
          .map(countsConfig => new InfluxReporter(environment, countsConfig))
          .map(reporter => new ProcessReportResources(reporter, counter, processRepository))
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
        system.shutdown()
      }
    })
  }

}