package pl.touk.esp.ui

import java.io.File

import _root_.cors.CorsSupport
import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import cats.data.Validated.{Invalid, Valid}
import cats.data.Xor
import com.typesafe.config.{ConfigFactory, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionMarshaller
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api._
import pl.touk.esp.ui.db.entity.EnvironmentsEntity.{EnvironmentsEntity, EnvironmentsEntityData}
import pl.touk.esp.ui.db.{DatabaseInitializer, EspTables}
import pl.touk.esp.ui.db.migration.{SampleData, SampleDataInserter}
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.security.SimpleAuthenticator
import slick.jdbc.JdbcBackend

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.io.Source.fromFile

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

  val processDefinition = loadProcessDefinition()
  val validator = ProcessValidator.default(processDefinition)
  val processValidation = new ProcessValidation(validator)
  val processConverter = new ProcessConverter(processValidation)

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile, processConverter)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile)

  val manager = FlinkProcessManager(config)

  val authenticator = new SimpleAuthenticator(config.getString("usersFile"))
  val environment = config.getString("environment")

  val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
  val route: Route = {

    CorsSupport.cors(isDevelopmentMode) {
      authenticateBasic("esp", authenticator) { user =>

        pathPrefix("api") {

          new ProcessesResources(processRepository, manager, processConverter, processValidation).route(user) ~
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

  init()
  def init() = {
    insertInitialProcesses()
    insertEnvironment(environment)
    if (isDevelopmentMode) {
      SampleDataInserter.insert(db)
    }
    Http().bindAndHandle(
      route,
      interface = "0.0.0.0",
      port = port
    )
  }

  def loadProcessDefinition(): ProcessDefinition[ObjectDefinition] = {
    val file = new File(initialProcessDirectory, "definition.json")
    ProcessDefinitionMarshaller.fromJson(fromFile(file).mkString) match {
      case Valid(definition) => definition
      case Invalid(error) => throw new IllegalArgumentException("Invalid process definition: " + error)
    }
  }

  def insertInitialProcesses(): Unit = {
    val toukUser = "TouK"
    new File(initialProcessDirectory, "processes").listFiles().foreach { file =>
      val name = file.getName.replaceAll("\\..*", "")
      for {
        latestVersion <- processRepository.fetchLatestProcessVersion(name)
        versionShouldBeUpdated = latestVersion.exists(_.user == toukUser) || latestVersion.isEmpty
        processJson = fromFile(file).mkString
        _ <- {
          if (versionShouldBeUpdated) {
            processRepository.saveProcess(name, GraphProcess(fromFile(file).mkString), toukUser)
          } else {
            logger.info(s"Process $name not updated. DB version is: \n${latestVersion.flatMap(_.json).getOrElse("")}\n and version from file is: \n$processJson")
            Future.successful(Xor.right(()))
          }
        }
      } yield ()
    }
    ConfigFactory.parseFile(new File(initialProcessDirectory, "customProcesses.conf"))
      .entrySet().toSet
      .foreach { (entry: java.util.Map.Entry[String, ConfigValue]) =>
        processRepository.saveProcess(entry.getKey, CustomProcess(entry.getValue.unwrapped().toString), toukUser)
      }
  }

  def insertEnvironment(environmentName: String) = {
    import DefaultJdbcProfile.profile.api._
    val insertAction = EspTables.environmentsTable += EnvironmentsEntityData(environmentName)
    db.run(insertAction).map(_ => ())
  }

}