package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy.ProcessActivity
import pl.touk.nussknacker.ui.api.utils.ScenarioDetailsOps.ScenarioWithDetailsOps
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.{
  DBIOActionRunner,
  FetchingProcessRepository,
  ScenarioWithDetailsEntity
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util._

import scala.concurrent.{ExecutionContext, Future}

class ProcessesExportResources(
    processRepository: FetchingProcessRepository[Future],
    protected val processService: ProcessService,
    scenarioActivityRepository: ScenarioActivityRepository,
    processResolvers: ProcessingTypeDataProvider[UIProcessResolver, _],
    dbioActionRunner: DBIOActionRunner,
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives
    with NuPathMatchers {

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  def securedRoute(implicit user: LoggedUser): Route = {
    path("processesExport" / ProcessNameSegment) { processName =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchLatestProcessDetailsForProcessId[ScenarioGraph](processId.id).map {
            exportProcess
          }
        }
      } ~ (post & processDetailsForName(processName)) { processDetails =>
        entity(as[ScenarioGraph]) { process =>
          complete {
            exportResolvedProcess(
              process,
              processDetails.processingType,
              processDetails.processVersionUnsafe,
              processDetails.isFragment,
            )
          }
        }
      }
    } ~ path("processesExport" / ProcessNameSegment / VersionIdSegment) { (processName, versionId) =>
      (get & processId(processName)) { processId =>
        complete {
          processRepository.fetchProcessDetailsForId[ScenarioGraph](processId.id, versionId).map {
            exportProcess
          }
        }
      }
    } ~ path("processesExport" / "pdf" / ProcessNameSegment / VersionIdSegment) { (processName, versionId) =>
      (post & processId(processName)) { processId =>
        entity(as[String]) { svg =>
          complete {
            processRepository.fetchProcessDetailsForId[ScenarioGraph](processId.id, versionId).flatMap { process =>
              dbioActionRunner.run {
                scenarioActivityRepository.findActivity(processId.id).map(exportProcessToPdf(svg, process, _))
              }
            }
          }
        }
      }
    }
  }

  private def exportProcess(processDetails: Option[ScenarioWithDetailsEntity[ScenarioGraph]]): HttpResponse =
    processDetails.map(details => exportProcess(details.json, details.name)).getOrElse {
      HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
    }

  private def exportProcess(processDetails: ScenarioGraph, name: ProcessName): HttpResponse = {
    fileResponse(CanonicalProcessConverter.fromScenarioGraph(processDetails, name))
  }

  private def exportResolvedProcess(
      processWithDictLabels: ScenarioGraph,
      processingType: ProcessingType,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(implicit user: LoggedUser): HttpResponse = {
    val processResolver = processResolvers.forProcessingTypeUnsafe(processingType)
    val resolvedProcess = processResolver.validateAndResolve(processWithDictLabels, processVersion, isFragment)
    fileResponse(resolvedProcess)
  }

  private def fileResponse(canonicalProcess: CanonicalProcess) = {
    val canonicalJson = canonicalProcess.asJson.spaces2
    val entity        = HttpEntity(ContentTypes.`application/json`, canonicalJson)
    AkkaHttpResponse.asFile(entity, s"${canonicalProcess.name}.json")
  }

  private def exportProcessToPdf(
      svg: String,
      processDetails: Option[ScenarioWithDetailsEntity[ScenarioGraph]],
      processActivity: ProcessActivity
  ) = processDetails match {
    case Some(process) =>
      val pdf = PdfExporter.exportToPdf(svg, process, processActivity)
      HttpResponse(status = StatusCodes.OK, entity = HttpEntity(pdf))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
  }

}
