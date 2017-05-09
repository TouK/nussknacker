package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, _}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.processreport.ProcessCountsReporter
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.{Argonaut62Support, DateUtils}
import pl.touk.process.report.influxdb.InfluxReporter

import scala.concurrent.{ExecutionContext, Future}

class ProcessCountsResources(influxReporter: InfluxReporter, processRepository: ProcessRepository)
                            (implicit ec: ExecutionContext) extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  case class NodeCount(all: Long, error: Long)

  def route(implicit loggedUser: LoggedUser): Route = {
    path("processCounts" / Segment) { processId =>
      get {
        parameters('dateFrom, 'dateTo) { (dateFrom, dateTo) =>
          complete {
            processRepository.fetchLatestProcessDetailsForProcessId(processId).flatMap[ToResponseMarshallable] { processOpt =>
              processOpt.flatMap(_.json) match {
                case None =>
                  Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
                case Some(process) =>
                  influxReporter.fetchBaseProcessCounts(processId, DateUtils.parseDateTime(dateFrom), DateUtils.parseDateTime(dateTo)).map { nodeResultsCount =>
                    val nodeIds = process.nodes.map(_.id)
                    val baseCounts = nodeResultsCount.mapToOriginalNodeIds(nodeIds)
                    new ProcessCountsReporter().reportCounts(process, baseCounts).mapValues(count => NodeCount(count, 0))
                  }
              }
            }
          }
        }
      }
    }
  }

}