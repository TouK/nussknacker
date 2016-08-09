package pl.touk.esp.ui

import _root_.db.migration.DefaultJdbcDriver
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import pl.touk.esp.ui.api.{ProcessesResources, WebResources}
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.repository.ProcessRepository
import slick.jdbc.JdbcBackend

object EspUiApp extends App with Directives {

  implicit val system = ActorSystem("esp-ui")
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  val db: JdbcBackend.Database = {
    val db = JdbcBackend.Database.forConfig("db", system.settings.config)
    new DatabaseInitializer(db).initDatabase()
    db
  }

  val processRepository = new ProcessRepository(db, DefaultJdbcDriver.driver)

  val route: Route =
    WebResources.route ~
    new ProcessesResources(processRepository).route

  Http().bindAndHandle(
    route,
    interface = "0.0.0.0",
    port = args(0).toInt
  )

}