package pl.touk.esp.ui

import java.io.File

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.ui.api._
import pl.touk.esp.ui.config.FeatureTogglesConfig
import pl.touk.esp.ui.validation.ProcessValidation
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.initialization.Initialization
import pl.touk.esp.ui.process.{JobStatusService, ProcessTypesForCategories, ProcessingTypeDeps}
import pl.touk.esp.ui.process.deployment.ManagementActor
import pl.touk.esp.ui.process.migrate.HttpProcessMigrator
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.esp.ui.process.subprocess.{ProcessRepositorySubprocessRepository, SubprocessResolver}
import pl.touk.esp.ui.process.uiconfig.SingleNodeConfig
import pl.touk.esp.ui.process.uiconfig.defaults.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.esp.ui.processreport.ProcessCounter
import pl.touk.esp.ui.security.SimpleAuthenticator
import pl.touk.process.report.influxdb.InfluxReporter
import slick.jdbc.JdbcBackend
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.util.Try

object EspUiApp extends App with Directives with LazyLogging {

  implicit val system = ActorSystem("esp-ui")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = system.settings.config
  val environment = config.getString("environment")
  val featureTogglesConfig = FeatureTogglesConfig.create(config, environment)
  val nodesConfig = Try(config.as[Map[String, SingleNodeConfig]](s"$environment.nodes")).getOrElse(Map.empty)
  logger.info(s"Ui config loaded: \nfeatureTogglesConfig: $featureTogglesConfig\nnodesConfig:$nodesConfig")

  val db: JdbcBackend.DatabaseDef = {
    val db = JdbcBackend.Database.forConfig("db", config)
    new DatabaseInitializer(db).initDatabase()
    db
  }

  val port = args(0).toInt
  val initialProcessDirectory = new File(args(1))
  val ProcessingTypeDeps(processDefinitions, validators, managers, espQueryableClient, buildInfo, standaloneModeEnabled) =
    ProcessingTypeDeps(config, featureTogglesConfig.standaloneMode)

  val processRepositoryForSub = new ProcessRepository(db, DefaultJdbcProfile.profile, null, system.dispatcher)
  val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map {case (k, v) => (k, v.defaultValues.getOrElse(Map.empty))})
  val extractValueParameterByConfigThenType = new TypeAfterConfig(defaultParametersValues)

  val subprocessRepository = new ProcessRepositorySubprocessRepository(processRepositoryForSub)
  val subprocessResolver = new SubprocessResolver(subprocessRepository)

  val processValidation = new ProcessValidation(validators, subprocessResolver)

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile, processValidation, system.dispatcher)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile, buildInfo)
  val processActivityRepository = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)
  val attachmentService = new ProcessAttachmentService(config.getString("attachmentsPath"), processActivityRepository)
  val authenticator = new SimpleAuthenticator(config.getString("usersFile"))
  val counter = new ProcessCounter(subprocessRepository)

  Initialization.init(processRepository, db, environment, featureTogglesConfig.development, initialProcessDirectory, standaloneModeEnabled)
  initHttp()

  val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository, subprocessResolver)
  val jobStatusService = new JobStatusService(managementActor)

  val typesForCategories = new ProcessTypesForCategories(config)


  private val apiResources : List[RouteWithUser] = {
    val routes = List(
      new ProcessesResources(processRepository, jobStatusService, processActivityRepository, processValidation, typesForCategories),
        new ProcessActivityResource(processActivityRepository, attachmentService),
        new ManagementResources(processDefinitions.values.flatMap(_.typesInformation).toList, counter, managementActor),
        new ValidationResources(processValidation),
        new DefinitionResources(processDefinitions, subprocessRepository, extractValueParameterByConfigThenType),
        new SignalsResources(managers, processDefinitions, processRepository),
        new QueryableStateResources(processDefinitions, processRepository, espQueryableClient, jobStatusService),
        new UserResources(),
        new SettingsResources(featureTogglesConfig, nodesConfig),
        new AppResources(buildInfo, processRepository, jobStatusService),
        new TestInfoResources(managers, processRepository)
    )
    val optionalRoutes = List(
      featureTogglesConfig.migration
        .map(migrationConfig => new HttpProcessMigrator(migrationConfig, environment))
        .map(migrator => new MigrationResources(migrator, processRepository)),
      featureTogglesConfig.counts
        .map(countsConfig => new InfluxReporter(environment, countsConfig))
        .map(reporter => new ProcessReportResources(reporter, counter, processRepository))
    ).flatten
    routes ++ optionalRoutes
  }

  def initHttp() = {
    val route: Route = {

        CorsSupport.cors(featureTogglesConfig.development) {
          authenticateBasic("esp", authenticator) { user =>

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

}