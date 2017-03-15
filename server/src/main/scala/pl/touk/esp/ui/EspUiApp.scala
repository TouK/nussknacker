package pl.touk.esp.ui

import java.io.File

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.ui.api._
import pl.touk.esp.ui.validation.ProcessValidation
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.initialization.Initialization
import pl.touk.esp.ui.process.deployment.ManagementActor
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.esp.ui.security.SimpleAuthenticator
import slick.jdbc.JdbcBackend

object EspUiApp extends App with Directives with LazyLogging {

  implicit val system = ActorSystem("esp-ui")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = system.settings.config

  val db: JdbcBackend.DatabaseDef = {
    val db = JdbcBackend.Database.forConfig("db", config)
    new DatabaseInitializer(db).initDatabase()
    db
  }

  val port = args(0).toInt
  val initialProcessDirectory = new File(args(1))
  val ProcessingTypeDeps(processDefinitions, validators, managers, buildInfo, standaloneModeEnabled) = ProcessingTypeDeps(config)

  val processValidation = new ProcessValidation(validators)

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile, processValidation)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile, buildInfo)
  val processActivityRepository = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)
  val attachmentService = new ProcessAttachmentService(config.getString("attachmentsPath"), processActivityRepository)


  val authenticator = new SimpleAuthenticator(config.getString("usersFile"))
  val environment = config.getString("environment")

  val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")


  Initialization.init(processRepository, db, environment, isDevelopmentMode, initialProcessDirectory, standaloneModeEnabled)
  initHttp()

  val managementActor = ManagementActor(environment, managers, processRepository, deploymentProcessRepository)

  def initHttp() = {
    val route: Route = {

        CorsSupport.cors(isDevelopmentMode) {
          authenticateBasic("esp", authenticator) { user =>

            pathPrefix("api") {

              new ProcessesResources(processRepository, managementActor, processActivityRepository, processValidation).route(user) ~
                new ProcessActivityResource(processActivityRepository, attachmentService).route(user) ~
                new ManagementResources(processDefinitions.values.flatMap(_.typesInformation).toList, managementActor).route(user) ~
                new ValidationResources(processValidation).route(user) ~
                new DefinitionResources(processDefinitions).route(user) ~
                new UserResources().route(user) ~
                new SettingsResources(config).route(user) ~
                new AppResources(buildInfo, processRepository, managementActor).route(user) ~
                new TestInfoResources(managers, processRepository).route(user)
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