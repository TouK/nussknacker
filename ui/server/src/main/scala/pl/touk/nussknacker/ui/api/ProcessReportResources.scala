package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, _}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.process.report.{CannotFetchCountsError, CountsReporter}

import scala.concurrent.{ExecutionContext, Future}

class ProcessReportResources(countsReporter: CountsReporter, processCounter: ProcessCounter, processRepository: FetchingProcessRepository)
                            (implicit ec: ExecutionContext) extends Directives with Argonaut62Support with UiCodecs with RouteWithUser {

  def route(implicit loggedUser: LoggedUser): Route = {
    path("processCounts" / Segment) { processId =>
      get {
        parameters('dateFrom, 'dateTo) { (dateFromS, dateToS) =>
          val dateTo = DateUtils.parseDateTime(dateToS)
          val dateToToUse = if (dateTo.isAfter(LocalDateTime.now())) LocalDateTime.now() else dateTo
          val dateFrom = DateUtils.parseDateTime(dateFromS)

          complete {
            processRepository.fetchLatestProcessDetailsForProcessId(processId).flatMap[ToResponseMarshallable] {
              case Some(process) =>
                process.json match {
                  case Some(displayable) => computeCounts(displayable, dateFrom, dateToToUse)
                  case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Counts unavailable for this process"))
                }
              case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
            }
          }
        }
      }
    }
  }


  private def computeCounts(process: DisplayableProcess, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[ToResponseMarshallable] = {
    countsReporter.prepareRawCounts(process.id, dateFrom, dateTo)
      .map(computeFinalCounts(process, _))
      .recover {
        case CannotFetchCountsError(msg) => HttpResponse(status = StatusCodes.BadRequest, entity = msg)
      }
  }


  private def computeFinalCounts(displayable: DisplayableProcess, nodeCountFunction: String => Option[Long]) : ToResponseMarshallable = {
    val computedCounts = processCounter.computeCounts(ProcessConverter.fromDisplayable(displayable),
      (nodeId) => nodeCountFunction(nodeId).map(count => RawCount(count, 0)))
    computedCounts.asJson
  }

}