package pl.touk.esp.ui

import java.io.File

import _root_.db.migration.DefaultJdbcDriver
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api.{ManagementResources, ProcessesResources, ValidationResources, WebResources}
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.repository.ProcessRepository
import slick.jdbc.JdbcBackend
import ch.megard.akka.http.cors.CorsDirectives._
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionMarshaller

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
  val jsonsDirectory = new File(args(1))

  val validator = ProcessValidator.default(loadProcessDefinition())

  val processRepository = new ProcessRepository(db, DefaultJdbcDriver.driver)
  insertInitialProcesses()

  val manager = FlinkProcessManager(config)

  val route: Route =
    cors() {
      pathPrefix("api") {
        new ProcessesResources(processRepository, manager).route ~
          new ManagementResources(processRepository, manager).route ~
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
    val file = new File(jsonsDirectory, "definition.json")
    ProcessDefinitionMarshaller.fromJson(scala.io.Source.fromFile(file).mkString) match {
      case Valid(definition) => definition
      case Invalid(error) => throw new IllegalArgumentException("Invalid process definition: " + error)
    }
  }

  def insertInitialProcesses(): Unit = {
    new File(jsonsDirectory, "processes").listFiles().foreach { file =>
      val name = file.getName.replaceAll("\\..*", "")
      processRepository.saveProcess(name, scala.io.Source.fromFile(file).mkString)
    }
  }

}