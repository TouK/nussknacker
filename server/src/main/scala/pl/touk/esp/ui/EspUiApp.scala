package pl.touk.esp.ui

import java.io.File

import _root_.db.migration.DefaultJdbcProfile
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import cats.data.Validated.{Invalid, Valid}
import ch.megard.akka.http.cors.CorsDirectives._
import com.typesafe.config.{ConfigFactory, ConfigValue}
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionMarshaller
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api.{ManagementResources, ProcessesResources, ValidationResources, WebResources}
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import slick.jdbc.JdbcBackend

import scala.collection.JavaConversions._
import scala.io.Source.fromFile

//todo  dodac test, ktory startuje aplikacje i sprawdza ze sie nie wywala
object EspUiApp extends App with Directives {

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

  val validator = ProcessValidator.default(loadProcessDefinition())

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile)

  insertInitialProcesses()

  val manager = FlinkProcessManager(config)

  val route: Route =
    cors() {
      pathPrefix("api") {
        new ProcessesResources(processRepository, manager, validator).route ~
          new ManagementResources(processRepository, deploymentProcessRepository, manager).route ~
          new ValidationResources(validator).route
      }
    } ~
      WebResources.route

  Http().bindAndHandle(
    route,
    interface = "0.0.0.0",
    port = port
  )

  def loadProcessDefinition(): ProcessDefinition = {
    val file = new File(initialProcessDirectory, "definition.json")
    ProcessDefinitionMarshaller.fromJson(fromFile(file).mkString) match {
      case Valid(definition) => definition
      case Invalid(error) => throw new IllegalArgumentException("Invalid process definition: " + error)
    }
  }

  def insertInitialProcesses(): Unit = {
    new File(initialProcessDirectory, "processes").listFiles().foreach { file =>
      val name = file.getName.replaceAll("\\..*", "")
      processRepository.saveProcess(name, GraphProcess(fromFile(file).mkString))
    }
    ConfigFactory.parseFile(new File(initialProcessDirectory, "customProcesses.conf"))
      .entrySet().toSet
      .foreach { (entry: java.util.Map.Entry[String, ConfigValue]) =>
        processRepository.saveProcess(entry.getKey, CustomProcess(entry.getValue.unwrapped().toString))
      }
    //do testow
//    fixme
  }

}