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
import pl.touk.esp.ui.process.values.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.esp.ui.processreport.ProcessCounter
import pl.touk.esp.ui.security.SimpleAuthenticator
import pl.touk.process.report.influxdb.InfluxReporter
import slick.jdbc.JdbcBackend
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object EspUiApp extends App with Directives with LazyLogging {

  implicit val system = ActorSystem("esp-ui")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = system.settings.config
  val environment = config.getString("environment")
  val featureTogglesConfig = FeatureTogglesConfig.create(config, environment)

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
  val defaultParametersValues = config.as[ParamDefaultValueConfig](s"$environment.defaultValues")
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

  def initHttp() = {
    val route: Route = {

        CorsSupport.cors(featureTogglesConfig.development) {
          authenticateBasic("esp", authenticator) { user =>

            pathPrefix("api") {
              val routes = List(
                new ProcessesResources(processRepository, jobStatusService, processActivityRepository, processValidation, typesForCategories).route(user),
                  new ProcessActivityResource(processActivityRepository, attachmentService).route(user),
                  new ManagementResources(processDefinitions.values.flatMap(_.typesInformation).toList, counter, managementActor).route(user),
                  new ValidationResources(processValidation).route(user),
                  new DefinitionResources(processDefinitions, subprocessRepository, extractValueParameterByConfigThenType).route(user),
                  new SignalsResources(managers, processDefinitions, processRepository).route(user),
                  new QueryableStateResources(processDefinitions, processRepository, espQueryableClient, jobStatusService).route(user),
                  new UserResources().route(user),
                  new SettingsResources(featureTogglesConfig).route(user),
                  new AppResources(buildInfo, processRepository, jobStatusService).route(user),
                  new TestInfoResources(managers, processRepository).route(user)
              )
              val optionalRoutes = List(
                featureTogglesConfig.migration
                  .map(migrationConfig => new HttpProcessMigrator(migrationConfig, environment))
                  .map(migrator => new MigrationResources(migrator, processRepository).route(user)),
                featureTogglesConfig.counts
                  .map(countsConfig => new InfluxReporter(environment, countsConfig))
                  .map(reporter => new ProcessReportResources(reporter, counter, processRepository).route(user))
              ).flatten
              (routes ++ optionalRoutes).reduce(_ ~ _)
            } ~
              //nie chcemy api, zeby nie miec problemow z autentykacja...
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