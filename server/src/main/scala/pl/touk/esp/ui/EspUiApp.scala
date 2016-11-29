package pl.touk.esp.ui

import java.io.File

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api._
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.initialization.{DefinitionLoader, Initialization}
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.{ProcessActivityRepository, DeployedProcessRepository, ProcessRepository}
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

  val processDefinition = DefinitionLoader.loadProcessDefinition(initialProcessDirectory)
  val validator = ProcessValidator.default(processDefinition)
  val processValidation = new ProcessValidation(validator)
  val processConverter = new ProcessConverter(processValidation)

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile, processConverter)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile)
  val commentsRepository = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)

  val manager = FlinkProcessManager(config)

  val authenticator = new SimpleAuthenticator(config.getString("usersFile"))
  val environment = config.getString("environment")

  val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")


  Initialization.init(processRepository, db, environment, isDevelopmentMode, initialProcessDirectory)
  initHttp()

  def initHttp() = {
    val route: Route = {

        CorsSupport.cors(isDevelopmentMode) {
          authenticateBasic("esp", authenticator) { user =>

            pathPrefix("api") {

              new ProcessesResources(processRepository, manager, processConverter, processValidation).route(user) ~
                new ProcessActivityResource(commentsRepository).route(user) ~
                new ManagementResources(processRepository, deploymentProcessRepository, manager, environment).route(user) ~
                new ValidationResources(processValidation, processConverter).route(user) ~
                new DefinitionResources(processDefinition).route(user) ~
                new UserResources().route(user) ~
                new SettingsResources(config).route(user)
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