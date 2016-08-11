package pl.touk.esp.ui

import java.io.File

import _root_.db.migration.DefaultJdbcDriver
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api.{ManagementResources, ProcessesResources, WebResources}
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.repository.ProcessRepository
import slick.jdbc.JdbcBackend
import ch.megard.akka.http.cors.CorsDirectives._

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

  val processRepository = new ProcessRepository(db, DefaultJdbcDriver.driver)
  val manager = FlinkProcessManager(config)

  val route: Route =
    cors() { pathPrefix("api") { new ProcessesResources(processRepository).route } } ~
    cors() { pathPrefix("api") { new ManagementResources(processRepository, manager).route } } ~
    WebResources.route


  val port = args(0).toInt
  val initialProcessLocation = args.drop(1).headOption

  insertInitialProcesses()

  Http().bindAndHandle(
    route,
    interface = "0.0.0.0",
    port = port
  )

  def insertInitialProcesses(): Unit = {
    initialProcessLocation.foreach { dir =>
      new File(dir).listFiles().foreach { file =>
        val name = file.getName.replaceAll("\\..*", "")
        processRepository.saveProcess(name, scala.io.Source.fromFile(file).mkString)
      }
    }
  }
}