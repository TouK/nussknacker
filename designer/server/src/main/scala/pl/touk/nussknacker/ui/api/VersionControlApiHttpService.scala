package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError.{
  MissingProcessId,
  MissingProcessVersion
}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery, ScenarioVersionQuery}
import pl.touk.nussknacker.ui.process.ProcessService.{GetScenarioWithDetailsOptions, SkipScenarioGraph}
import pl.touk.nussknacker.ui.process.repository.ScenarioVersionMetadata
import pl.touk.nussknacker.ui.security.api.AuthManager

import scala.concurrent.{ExecutionContext, Future}

class VersionControlApiHttpService(
    authManager: AuthManager,
    processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {
  private val securityInput = authManager.authenticationEndpointInput()

  private val endpoints = new VersionControlApiEndpoints(securityInput)

  expose {
    endpoints.versionValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[VersionControlError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, processVersionValidationRequest) =>
          for {
            pid <- EitherT.fromOptionF(processService.getProcessId(scenarioName), MissingProcessId(scenarioName.value))

            processWithDetails <- EitherT.right(
              processService.getLatestProcessWithDetails(
                ProcessIdWithName(pid, scenarioName),
                GetScenarioWithDetailsOptions(SkipScenarioGraph, false)
              )
            )

            localVersion          = processVersionValidationRequest.localVersion
            latestDeployedVersion = processWithDetails.lastDeployedAction.map(_.processVersionId).map(_.value)
            latestVersion         = processWithDetails.processVersionId.value
            modifiedBy            = processWithDetails.modifiedBy

            response = ProcessVersionValidationResponseDto(
              processName = scenarioName.value,
              isLatest = localVersion == latestVersion,
              localVersion = localVersion,
              latestDeployedVersion = latestDeployedVersion,
              latestVersion = latestVersion,
              modifiedBy = modifiedBy
            )
          } yield response
        }
      }
  }

}
