package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, _}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.processCounts._

import scala.concurrent.{ExecutionContext, Future}

class ProcessReportResources(countsReporter: CountsReporter, processCounter: ProcessCounter, val processRepository: FetchingProcessRepository[Future])
                            (implicit val ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser with ProcessDirectives {

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    path("processCounts" / Segment) { processName =>
      (get & processId(processName)) { processId =>
        parameterMap { parameters =>
          val request = prepareRequest(parameters)
          complete {
            processRepository.fetchLatestProcessDetailsForProcessId[DisplayableProcess](processId.id).flatMap[ToResponseMarshallable] {
              case Some(process) =>
                process.json match {
                  case Some(displayable) => computeCounts(displayable, request)
                  case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Counts unavailable for this scenario"))
                }
              case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found"))
            }
          }
        }
      }
    }
  }

  private def prepareRequest(parameters: Map[String, String]): CountsRequest = {
    def getAsDate(paramName: String) = parameters.get(paramName).filterNot(_.isEmpty).map(DateUtils.parseDateTime)
    val dateTo = getAsDate("dateTo")
      .filterNot(_.isAfter(LocalDateTime.now()))
      .getOrElse(LocalDateTime.now())
    getAsDate("dateFrom") match {
      case Some(dateFrom) =>
        RangeCount(dateFrom, dateTo)
      case None =>
        ExecutionCount(dateTo)
    }
  }

  private def computeCounts(process: DisplayableProcess, countsRequest: CountsRequest): Future[ToResponseMarshallable] = {
    countsReporter.prepareRawCounts(process.id, countsRequest)
      .map(computeFinalCounts(process, _))
      .recover {
        case CannotFetchCountsError(msg) => HttpResponse(status = StatusCodes.BadRequest, entity = msg)
      }
  }


  private def computeFinalCounts(displayable: DisplayableProcess, nodeCountFunction: String => Option[Long]) : ToResponseMarshallable = {
    val computedCounts = processCounter.computeCounts(ProcessConverter.fromDisplayable(displayable),
      nodeId => nodeCountFunction(nodeId).map(count => RawCount(count, 0)))
    computedCounts.asJson
  }

}