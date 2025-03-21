package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError.{
  MissingProcessId,
  MissingProcessVersion
}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery, ScenarioVersionQuery}
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
            versionsMap <- EitherT.right(
              processService.getLatestVersionForProcesses(
                ScenarioQuery(names = Some(Seq(scenarioName))),
                ScenarioVersionQuery(None)
              )
            )
            scenarioVersionMetadata <- EitherT.fromOptionF[Future, VersionControlError, ScenarioVersionMetadata](
              Future.successful(versionsMap.get(pid)),
              MissingProcessVersion(scenarioName.value)
            )

            localVersion  = processVersionValidationRequest.localVersion
            latestVersion = scenarioVersionMetadata.versionId.value

            response = ProcessVersionValidationResponseDto(
              processName = scenarioName.value,
              isLatest = localVersion == latestVersion,
              localVersion = localVersion,
              latestVersion = latestVersion
            )
          } yield response
        }
      }
  }

}
