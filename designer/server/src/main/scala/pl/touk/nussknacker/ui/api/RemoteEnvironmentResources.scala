package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.restmodel.ValidatedProcessDetails
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.process.{ProcessService, ProcessesQuery}
import pl.touk.nussknacker.ui.process.ProcessService.{FetchScenarioGraph, GetScenarioWithDetailsOptions}
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{EspPathMatchers, ProcessComparator}

import scala.concurrent.{ExecutionContext, Future}

class RemoteEnvironmentResources(
    remoteEnvironment: RemoteEnvironment,
    protected val processService: ProcessService,
    val processAuthorizer: AuthorizeProcess
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives
    with EspPathMatchers {

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("remoteEnvironment") {
      path("compare") {
        get {
          complete {
            for {
              processes <- processService.getProcessesWithDetails(
                ProcessesQuery.unarchived,
                GetScenarioWithDetailsOptions.withsScenarioGraph
              )
              comparison <- compareProcesses(processes)
            } yield NuDesignerErrorToHttp.toResponseEither(comparison)
          }
        }
      } ~
        path(Segment / VersionIdSegment / "compare" / VersionIdSegment) { (processName, version, otherVersion) =>
          (get & processId(processName)) { processIdWithName =>
            complete {
              withProcess(
                processIdWithName,
                version,
                (process, _) => remoteEnvironment.compare(process, Some(otherVersion))
              )
            }
          }
        } ~
        path(Segment / VersionIdSegment / "migrate") { (processName, version) =>
          (post & processId(processName)) { processIdWithName =>
            complete {
              withProcess(processIdWithName, version, remoteEnvironment.migrate)
            }
          }
        } ~
        path(Segment / "versions") { processName =>
          (get & processId(processName)) { processId =>
            complete {
              remoteEnvironment.processVersions(processId.name)
            }
          }
        }
    }
  }

  private def compareProcesses(
      processes: List[ValidatedProcessDetails]
  )(implicit ec: ExecutionContext): Future[Either[NuDesignerError, EnvironmentComparisonResult]] = {
    val results = Future.sequence(processes.map(p => compareOneProcess(p.scenarioGraphUnsafe)))
    results.map { comparisonResult =>
      comparisonResult
        .sequence[XError, ProcessDifference]
        .map(_.filterNot(_.areSame))
        .map(EnvironmentComparisonResult.apply)
    }
  }

  private def withProcess[T: Encoder](
      processIdWithName: ProcessIdWithName,
      version: VersionId,
      fun: (DisplayableProcess, String) => Future[Either[NuDesignerError, T]]
  )(implicit user: LoggedUser) = {
    processService
      .getProcessWithDetails(
        processIdWithName,
        version,
        GetScenarioWithDetailsOptions.withsScenarioGraph
      )
      .flatMap(details => fun(details.scenarioGraphUnsafe, details.processCategory))
      .map(NuDesignerErrorToHttp.toResponseEither[T])
  }

  private def compareOneProcess(
      process: DisplayableProcess
  )(implicit ec: ExecutionContext): Future[XError[ProcessDifference]] = {
    remoteEnvironment.compare(process, None).map {
      case Right(differences) => Right(ProcessDifference(process.id, presentOnOther = true, differences))
      case Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _)) =>
        Right(ProcessDifference(process.id, presentOnOther = false, Map()))
      case Left(error) => Left(error)
    }
  }

}

//we make additional class here to be able to e.g. compare model versions...
@JsonCodec final case class EnvironmentComparisonResult(processDifferences: List[ProcessDifference])

@JsonCodec final case class ProcessDifference(
    id: String,
    presentOnOther: Boolean,
    differences: Map[String, ProcessComparator.Difference]
) {
  def areSame: Boolean = presentOnOther && differences.isEmpty
}
