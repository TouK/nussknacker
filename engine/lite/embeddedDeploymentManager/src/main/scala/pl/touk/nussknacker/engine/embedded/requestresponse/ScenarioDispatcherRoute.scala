package pl.touk.nussknacker.engine.embedded.requestresponse

import akka.event.Logging
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import akka.stream.Materializer
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.lite.requestresponse.ScenarioRoute

import scala.concurrent.ExecutionContext

class ScenarioDispatcherRoute(scenarioRoutes: scala.collection.Map[String, ScenarioRoute]) extends Directives with LazyLogging {

  protected def logDirective(scenarioName: String): Directive0 = DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  def route(implicit ec: ExecutionContext, mat: Materializer): Route =
    path("scenario" / Segment) { scenarioSlug =>
      handle(scenarioSlug)(_.invocationRoute)
    } ~
      path("scenario" / Segment / "definition") { scenarioSlug =>
        handle(scenarioSlug)(_.definitionRoute)
      }

  private def handle(scenarioSlug: String)(callback: ScenarioRoute => Route): Route = scenarioRoutes.get(scenarioSlug) match {
    case None => complete {
      HttpResponse(status = StatusCodes.NotFound)
    }
    case Some(processInterpreter) => callback(processInterpreter)
  }

}

object ScenarioDispatcherRoute {

  def invocationUrl(scenarioSlug: String) = s"/scenario/$scenarioSlug"

}