package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.LoggedUserUtils.Ops
import pl.touk.nussknacker.ui.util.{NuPathMatchers, ScenarioGraphComparator}

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}

class RemoteEnvironmentResources(
    remoteEnvironment: RemoteEnvironment,
    protected val processService: ProcessService,
    val processAuthorizer: AuthorizeProcess,
    scenarioActivityRepository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner,
    clock: Clock,
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives
    with NuPathMatchers {

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("remoteEnvironment") {
      // TODO This endpoint is used by an external project. We should consider moving its logic to this project
      //      Currently it only compose result of processes endpoints and an endpoint below but with
      //      the latest remote version instead of the specific one
      path("compare") {
        get {
          complete {
            for {
              processes <- processService.getLatestProcessesWithDetails(
                ScenarioQuery.unarchived,
                GetScenarioWithDetailsOptions.withsScenarioGraph
              )
              comparison <- compareProcesses(processes)
            } yield NuDesignerErrorToHttp.toResponseEither(comparison)
          }
        }
      } ~
        path(ProcessNameSegment / VersionIdSegment / "compare" / VersionIdSegment) {
          (processName, version, otherVersion) =>
            (get & processId(processName)) { processIdWithName =>
              complete {
                withProcess(
                  processIdWithName,
                  version,
                  details =>
                    remoteEnvironment.compare(details.scenarioGraphUnsafe, processIdWithName.name, Some(otherVersion))
                )
              }
            }
        } ~
        path(ProcessNameSegment / VersionIdSegment / "migrate") { (processName, version) =>
          (post & processId(processName)) { processIdWithName =>
            complete {
              withProcess(
                processIdWithName,
                version,
                details =>
                  for {
                    result <- remoteEnvironment.migrate(
                      details.processingMode,
                      details.engineSetupName,
                      details.processCategory,
                      details.labels,
                      details.scenarioGraphUnsafe,
                      details.processVersionId,
                      details.name,
                      details.isFragment
                    )
                    _ <- dbioActionRunner.run(
                      scenarioActivityRepository.addActivity(
                        ScenarioActivity.OutgoingMigration(
                          scenarioId = ScenarioId(processIdWithName.id.value),
                          scenarioActivityId = ScenarioActivityId.random,
                          user = user.scenarioUser,
                          date = clock.instant(),
                          scenarioVersionId = Some(ScenarioVersionId.from(details.processVersionId)),
                          destinationEnvironment = Environment(remoteEnvironment.environmentId)
                        )
                      )
                    )
                  } yield result
              )
            }
          }
        } ~
        path(ProcessNameSegment / "versions") { processName =>
          (get & processId(processName)) { processId =>
            complete {
              remoteEnvironment.processVersions(processId.name)
            }
          }
        }
    }
  }

  private def compareProcesses(
      processes: List[ScenarioWithDetails]
  )(implicit ec: ExecutionContext): Future[Either[NuDesignerError, EnvironmentComparisonResult]] = {
    val results = Future.sequence(processes.map(compareOneProcess))
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
      fun: ScenarioWithDetails => Future[Either[NuDesignerError, T]]
  )(implicit user: LoggedUser) = {
    processService
      .getProcessWithDetails(
        processIdWithName,
        version,
        GetScenarioWithDetailsOptions.withsScenarioGraph
      )
      .flatMap(fun)
      .map(NuDesignerErrorToHttp.toResponseEither[T])
  }

  private def compareOneProcess(
      scenarioWithDetails: ScenarioWithDetails
  )(implicit ec: ExecutionContext): Future[XError[ProcessDifference]] = {
    remoteEnvironment.compare(scenarioWithDetails.scenarioGraphUnsafe, scenarioWithDetails.name, None).map {
      case Right(differences) => Right(ProcessDifference(scenarioWithDetails.name, presentOnOther = true, differences))
      case Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _)) =>
        Right(ProcessDifference(scenarioWithDetails.name, presentOnOther = false, Map()))
      case Left(error) => Left(error)
    }
  }

}

//we make additional class here to be able to e.g. compare model versions...
@JsonCodec final case class EnvironmentComparisonResult(processDifferences: List[ProcessDifference])

@JsonCodec final case class ProcessDifference(
    name: ProcessName,
    presentOnOther: Boolean,
    differences: Map[String, ScenarioGraphComparator.Difference]
) {

  def areSame: Boolean = presentOnOther && differences.isEmpty
}
