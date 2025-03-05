package pl.touk.nussknacker.engine.embedded.requestresponse

import akka.event.Logging
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

class ScenarioDispatcherRoute(scenarioRoutes: scala.collection.Map[String, Route]) extends Directives with LazyLogging {

  protected def logDirective(scenarioName: String): Directive0 =
    DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  def route(implicit ec: ExecutionContext, mat: Materializer): Route =
    pathPrefix("scenario" / Segment) { scenarioSlug =>
      handle(scenarioSlug)
    }

  private def handle(scenarioSlug: String): Route = scenarioRoutes.get(scenarioSlug) match {
    case None =>
      complete {
        HttpResponse(status = StatusCodes.NotFound)
      }
    case Some(r) => r
  }

}
