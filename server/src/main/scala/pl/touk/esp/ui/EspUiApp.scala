package pl.touk.esp.ui

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.ui.api.{ProcessesResources, WebResources}
import pl.touk.esp.ui.core.process.marshall.ProcessConverter
import pl.touk.esp.ui.core.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.sample.SampleProcess

import scala.concurrent.Future

object EspUiApp extends App with Directives {

  implicit val system = ActorSystem("esp-ui")
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  def sampleProcess(id: String): Future[Option[DisplayableProcess]] =
    Future.successful(
      Some(
        ProcessConverter.toDisplayable(
          ProcessCanonizer.canonize(
            SampleProcess.prepareProcess()
          )
        )
      )
    )

  val route: Route =
    WebResources.route ~
    new ProcessesResources(sampleProcess).route

  Http().bindAndHandle(
    route,
    interface = "0.0.0.0",
    port = args(0).toInt
  )

}