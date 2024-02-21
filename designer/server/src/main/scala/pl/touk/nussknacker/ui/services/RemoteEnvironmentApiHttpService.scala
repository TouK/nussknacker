package pl.touk.nussknacker.ui.services

import akka.http.scaladsl.model.StatusCodes
import cats.data.EitherT
import cats.syntax.traverse._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.{EnvironmentComparisonResult, ProcessDifference, RemoteEnvironmentApiEndpoints}
import pl.touk.nussknacker.ui.api.RemoteEnvironmentApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError}

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.ui.util.EitherTImplicits

class RemoteEnvironmentApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processService: ProcessService,
    remoteEnvironment: RemoteEnvironment
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {
  import EitherTImplicits._

  private val remoteEnvironmentApiEndpoints = new RemoteEnvironmentApiEndpoints(authenticator.authenticationMethod())

  expose {
    remoteEnvironmentApiEndpoints.compareEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { loggedUser => _ =>
        EitherT(for {
          processes <- processService.getLatestProcessesWithDetails(
            ScenarioQuery.unarchived,
            GetScenarioWithDetailsOptions.withsScenarioGraph
          )(loggedUser)
          comparison <- compareProcesses(processes)
        } yield comparison)
      }
  }

  expose {
    remoteEnvironmentApiEndpoints.compareTwoVersionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { loggedUser =>
        {
          case (processName, versionId, otherVersionId) => {
            implicit val user: LoggedUser = loggedUser
            EitherT(
              for {
                pid <- processService.getProcessIdUnsafe(processName)
                processIdWithName = ProcessIdWithName(pid, processName)
                res <- withProcess[ComparisonDifferences](
                  processIdWithName,
                  versionId,
                  details =>
                    remoteEnvironment.compare(
                      details.scenarioGraphUnsafe,
                      processIdWithName.name,
                      Some(otherVersionId)
                    )
                )
              } yield res
            )
          }
        }
      }
  }

  private def withProcess[T: Encoder](
      processIdWithName: ProcessIdWithName,
      version: VersionId,
      fun: ScenarioWithDetails => Future[Either[pl.touk.nussknacker.ui.NuDesignerError, T]]
  )(implicit user: LoggedUser) = {
    processService
      .getProcessWithDetails(
        processIdWithName,
        version,
        GetScenarioWithDetailsOptions.withsScenarioGraph
      )
      .flatMap(fun)
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
