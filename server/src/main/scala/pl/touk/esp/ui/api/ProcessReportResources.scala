package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, _}
import pl.touk.esp.ui.codec.UiCodecs
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.DateUtils
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.process.report.influxdb.InfluxReporter

import scala.concurrent.{ExecutionContext, Future}

class ProcessReportResources(influxReporter: InfluxReporter, processCounter: ProcessCounter, processRepository: ProcessRepository)
                            (implicit ec: ExecutionContext) extends Directives with Argonaut62Support with UiCodecs {

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
                deploymentsBetweenDates(process, dateFrom, dateToToUse) match {
                  case Some(date) =>
                    Future.successful(HttpResponse(status = StatusCodes.BadRequest, entity = s"Counts unavailable, as process was deployed on $date"))
                  case None => process.json match {
                    case Some(displayable) => computeCounts(displayable, dateFrom, dateToToUse)
                    case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Counts unavailable for this process"))
                  }
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
      val nodeIds = displayable.nodes.map(_.id)
      val baseCounts = nodeResultsCount.mapToOriginalNodeIds(nodeIds)
      val computedCounts = processCounter.computeCounts(ProcessConverter.fromDisplayable(displayable),
        baseCounts.nodes.mapValues(value =>  RawCount(value, 0)))
      computedCounts.asJson
    }
  }

  //TODO: this doesn't take into account redeploys done only in flink (e.g. restarting after error)
  private def deploymentsBetweenDates(process: ProcessDetails, fromDate: LocalDateTime, toDate: LocalDateTime) : Option[LocalDateTime] = {
    process.history.flatMap(_.deployments).map(_.deployedAt).find(d => fromDate.isBefore(d) && toDate.isAfter(d))
  }

}