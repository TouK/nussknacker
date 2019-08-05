package pl.touk.nussknacker.ui.api

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller, MarshallOptions}
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util._

import scala.concurrent.ExecutionContext

class ProcessesExportResources(val processRepository: FetchingProcessRepository,
                               processActivityRepository: ProcessActivityRepository)
                              (implicit val ec: ExecutionContext, mat: Materializer, jsonMarshaller: JsonMarshaller)
  extends Directives with Argonaut62Support with RouteWithUser with UiCodecs with ProcessDirectives {

  def route(implicit user: LoggedUser): Route = {
    path("processesExport" / Segment) { processName =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchLatestProcessDetailsForProcessId[DisplayableProcess](processId.id).map {
            exportProcess
          }
        }
      }
    } ~ path("processesExport" / Segment / LongNumber) { (processName, versionId) =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId, businessView = false).map {
            exportProcess
          }
        }
      }
    } ~ path("processesExport" / "pdf" / Segment / LongNumber) { (processName, versionId) =>
      parameter('businessView ? false) { businessView =>
        (post & processId(processName)) { processId =>
          entity(as[Array[Byte]]) { svg =>
            complete {
              processRepository.fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId, businessView).flatMap { process =>
                processActivityRepository.findActivity(processId).map(exportProcessToPdf(new String(svg, StandardCharsets.UTF_8), process, _))
              }
            }
          }
        }
      }
    } ~ path("processesExport") {
      post {
        entity(as[DisplayableProcess]) { process =>
          complete {
            val json = UiProcessMarshaller.toJson(ProcessConverter.fromDisplayable(process))
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), jsonMarshaller.marshall(json, MarshallOptions(pretty = true))))
          }
        }
      }
    }
  }

  private def exportProcess(processDetails: Option[ProcessDetails]): HttpResponse = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        jsonMarshaller.marshall(UiProcessMarshaller.toJson(ProcessConverter.fromDisplayable(json)), MarshallOptions(pretty = true))
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