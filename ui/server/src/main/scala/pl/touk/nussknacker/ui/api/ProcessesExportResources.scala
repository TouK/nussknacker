package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util._

import scala.concurrent.ExecutionContext

class ProcessesExportResources(val processRepository: FetchingProcessRepository,
                               processActivityRepository: ProcessActivityRepository)
                              (implicit val ec: ExecutionContext, mat: Materializer)
  extends Directives with FailFastCirceSupport with RouteWithUser with ProcessDirectives {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)


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
          entity(as[String]) { svg =>
            complete {
              processRepository.fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId, businessView).flatMap { process =>
                processActivityRepository.findActivity(processId).map(exportProcessToPdf(svg, process, _))
              }
            }
          }
        }
      }
    } ~ path("processesExport") {
      post {
        entity(as[DisplayableProcess]) { process =>
          complete {
            exportProcess(process)
          }
        }
      }
    }
  }

  private def exportProcess(processDetails: Option[ProcessDetails]): HttpResponse = processDetails.flatMap(_.json) match {
    case Some(displayableProcess) =>
      exportProcess(displayableProcess)
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

  private def exportProcess(processDetails: DisplayableProcess): HttpResponse = {
    val canonicalJson = ProcessMarshaller.toJson(ProcessConverter.fromDisplayable(processDetails)).spaces2
    val entity = HttpEntity(ContentTypes.`application/json`, canonicalJson)
    AkkaHttpResponse.asFile(entity, s"${processDetails.id}.json")
  }

  private def exportProcessToPdf(svg: String, processDetails: Option[ProcessDetails], processActivity: ProcessActivity) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        PdfExporter.exportToPdf(svg, process, processActivity, json)
      }.map { pdf =>
        HttpResponse(status = StatusCodes.OK, entity = HttpEntity(pdf))
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

}