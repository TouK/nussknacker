package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util._
import io.circe.syntax._
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

import scala.concurrent.{ExecutionContext, Future}

class ProcessesExportResources(val processRepository: FetchingProcessRepository[Future],
                               processActivityRepository: ProcessActivityRepository,
                               processResolving: UIProcessResolving)
                              (implicit val ec: ExecutionContext, mat: Materializer)
  extends Directives with FailFastCirceSupport with RouteWithUser with ProcessDirectives with EspPathMatchers {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)


  def securedRoute(implicit user: LoggedUser): Route = {
    path("processesExport" / Segment) { processName =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchLatestProcessDetailsForProcessId[DisplayableProcess](processId.id).map {
            exportProcess
          }
        }
      }
    } ~ path("processesExport" / Segment / VersionIdSegment) { (processName, versionId) =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId).map {
            exportProcess
          }
        }
      }
    } ~ path("processesExport" / "pdf" / Segment / VersionIdSegment) { (processName, versionId) =>
      (post & processId(processName)) { processId =>
        entity(as[String]) { svg =>
          complete {
            processRepository.fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId).flatMap { process =>
              processActivityRepository.findActivity(processId).map(exportProcessToPdf(svg, process, _))
            }
          }
        }
      }
    } ~ path("processesExport") {
      post {
        entity(as[DisplayableProcess]) { process =>
          processIdWithCategory(process.id) { idWithCategory =>
            complete {
              // here we gets process from UI, so we need to resolve it before we export it
              exportResolvedProcess(process, idWithCategory.category)
            }
          }
        }
      }
    }
  }

  private def exportProcess(processDetails: Option[ProcessDetails]): HttpResponse = processDetails.map(_.json) match {
    case Some(displayableProcess) =>
      exportProcess(displayableProcess)
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
  }

  private def exportProcess(processDetails: DisplayableProcess): HttpResponse = {
    fileResponse(ProcessConverter.fromDisplayable(processDetails))
  }

  private def exportResolvedProcess(processWithDictLabels: DisplayableProcess, category: Category): HttpResponse = {
    val validationResult = processResolving.validateBeforeUiResolving(processWithDictLabels, category)
    val resolvedProcess = processResolving.resolveExpressions(processWithDictLabels, validationResult.typingInfo)
    fileResponse(resolvedProcess)
  }

  private def fileResponse(canonicalProcess: CanonicalProcess) = {
    val canonicalJson = canonicalProcess.asJson.spaces2
    val entity = HttpEntity(ContentTypes.`application/json`, canonicalJson)
    AkkaHttpResponse.asFile(entity, s"${canonicalProcess.metaData.id}.json")
  }

  private def exportProcessToPdf(svg: String, processDetails: Option[ProcessDetails], processActivity: ProcessActivity) = processDetails match {
    case Some(process) =>
      val pdf = PdfExporter.exportToPdf(svg, process, processActivity)
      HttpResponse(status = StatusCodes.OK, entity = HttpEntity(pdf))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
  }

}
