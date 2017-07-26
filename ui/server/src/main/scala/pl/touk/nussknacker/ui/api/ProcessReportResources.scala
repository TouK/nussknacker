package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, _}
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.processreport.{ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.process.report.influxdb.InfluxReporter

import scala.concurrent.{ExecutionContext, Future}

class ProcessReportResources(influxReporter: InfluxReporter, processCounter: ProcessCounter, processRepository: ProcessRepository)
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
                restartsBetweenDates(process, dateFrom, dateToToUse).flatMap {
                  case Nil => process.json match {
                    case Some(displayable) => computeCounts(displayable, dateFrom, dateToToUse)
                    case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Counts unavailable for this process"))
                  }
                  case dates => Future.successful(HttpResponse(status = StatusCodes.BadRequest,
                    entity = s"Counts unavailable, as process was restarted/deployed on " +
                      s" following dates: ${dates.map(_.format(DateUtils.dateTimeFormatter)).mkString(", ")}"))
                }
              case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
            }
          }
        }
      }
    }
  }

  private def computeCounts(displayable: DisplayableProcess, dateFrom: LocalDateTime, dateTo: LocalDateTime) : Future[ToResponseMarshallable] = {
    influxReporter.fetchBaseProcessCounts(displayable.id, dateFrom, dateTo).map { nodeResultsCount =>
      val computedCounts = processCounter.computeCounts(ProcessConverter.fromDisplayable(displayable),
        (nodeId) => nodeResultsCount.getCountForNodeId(nodeId).map(count => RawCount(count, 0)))
      computedCounts.asJson
    }
  }

  private def restartsBetweenDates(process: ProcessDetails, fromDate: LocalDateTime, toDate: LocalDateTime) : Future[List[LocalDateTime]] = {
    influxReporter.detectRestarts(process.id, fromDate, toDate)
  }

}