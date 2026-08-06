package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService
import pl.touk.nussknacker.ui.process.migrate.{
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError,
  RemoteScenarioVersions
}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{NuPathMatchers, ScenarioGraphComparator}
import pl.touk.nussknacker.ui.util.LoggedUserUtils.Ops

import java.time.Clock
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
    with NuPathMatchers
    with VersionsToCompareDirective {

  private val versionsWithDifferencesService = new VersionsWithDifferencesService(processService)

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
                GetScenarioWithDetailsOptions.withScenarioGraph
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
                  GetScenarioWithDetailsOptions.withScenarioGraph,
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
                // Scenario validation is needed in order to validate and resolve
                // dictionaries before sending migration request to remote environment.
                GetScenarioWithDetailsOptions.withScenarioGraph.withValidation,
                details =>
                  {
                    for {
                      result <- EitherT(
                        remoteEnvironment.migrate(
                          details.processingMode,
                          details.engineSetupName,
                          details.processCategory,
                          details.labels,
                          details.scenarioGraphUnsafe,
                          details.processVersionId,
                          details.name,
                          details.isFragment
                        )
                      )
                      _ <- EitherT.right[NuDesignerError](
                        dbioActionRunner.run(
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
                      )
                    } yield result
                  }.value
              )
            }
          }
        } ~
        path(ProcessNameSegment / VersionIdSegment / "versions-with-differences") {
          (processName, currentLocalVersionId) =>
            (get & processId(processName) & versionsToCompare) { (processIdWithName, limit) =>
              // the remote is queried with the designer's own service account, so this has to gate it
              canRead(processIdWithName) {
                complete {
                  versionsWithDifferencesService.computeForRemoteVersions(
                    remoteEnvironment,
                    processIdWithName,
                    currentLocalVersionId,
                    limit
                  )
                }
              }
            }
        } ~
        path(ProcessNameSegment / "versions") { processName =>
          (get & processId(processName)) { processId =>
            canRead(processId) {
              onSuccess(remoteEnvironment.processVersions(processId.name)) {
                case RemoteScenarioVersions(versions, false) =>
                  complete(versions)
                case RemoteScenarioVersions(_, true) =>
                  complete(
                    StatusCodes.BadGateway,
                    s"Could not fetch scenario versions from the ${remoteEnvironment.environmentId} environment"
                  )
              }
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
      fetchOptions: GetScenarioWithDetailsOptions,
      fun: ScenarioWithDetails => Future[Either[NuDesignerError, T]],
  )(implicit user: LoggedUser) = {
    processService
      .getProcessWithDetails(processIdWithName, version, fetchOptions)
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
