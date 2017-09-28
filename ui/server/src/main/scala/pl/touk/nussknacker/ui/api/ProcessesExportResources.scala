package pl.touk.nussknacker.ui.api

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import argonaut._
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util._

import scala.concurrent.ExecutionContext

class ProcessesExportResources(repository: FetchingProcessRepository,
                               processActivityRepository: ProcessActivityRepository)
                              (implicit ec: ExecutionContext, mat: Materializer)
  extends Directives with Argonaut62Support with RouteWithUser with UiCodecs {

  def route(implicit user: LoggedUser): Route = {
    path("processes" / "export" / Segment) { processId =>
      get {
        complete {
          repository.fetchLatestProcessDetailsForProcessId(processId).map {
            exportProcess
          }
        }
      }
    } ~ path("processes" / "export" / Segment / LongNumber) { (processId, versionId) =>
      get {
        complete {
          repository.fetchProcessDetailsForId(processId, versionId, businessView = false).map {
            exportProcess
          }
        }
      }
    } ~ path("processes" / "exportToPdf" / Segment / LongNumber) { (processId, versionId) =>
      parameter('businessView ? false) { (businessView) =>
        post {
          entity(as[Array[Byte]]) { (svg) =>
            complete {
              repository.fetchProcessDetailsForId(processId, versionId, businessView).flatMap { process =>
                processActivityRepository.findActivity(processId).map(exportProcessToPdf(new String(svg, StandardCharsets.UTF_8), process, _))
              }
            }
          }
        }
      }
    } ~ path("processes" / "convert") {
      post {
        entity(as[DisplayableProcess]) { process =>
          complete {
            val json = UiProcessMarshaller.toJson(ProcessConverter.fromDisplayable(process), PrettyParams.spaces2)
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), json))
          }
        }
      }
    }
  }

  private def exportProcess(processDetails: Option[ProcessDetails]) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        UiProcessMarshaller.toJson(ProcessConverter.fromDisplayable(json), PrettyParams.spaces2)
      }.map { canonicalJson =>
        AkkaHttpResponse.asFile(canonicalJson, s"${process.id}.json")
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

  private def exportProcessToPdf(svg: String, processDetails: Option[ProcessDetails], processActivity: ProcessActivity) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        PdfExporter.exportToPdf(svg, process, processActivity, json)
      }.map { pdf =>
        HttpResponse(status = StatusCodes.OK, entity = pdf)
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

}