package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.newactivity.ActivityService
import pl.touk.nussknacker.ui.process.newactivity.ActivityService.UnderlyingServiceError
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    activityService: ActivityService,
    deploymentService: DeploymentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.runDeploymentEndpoint
      .serverSecurityLogic(authorizeKnownUser[RunDeploymentError])
      .serverLogicFlatErrors { implicit loggedUser =>
        { case (deploymentId, request) =>
          activityService
            .processCommand(
              RunDeploymentCommand(
                id = deploymentId,
                scenarioName = request.scenarioName,
                nodesDeploymentData = request.nodesDeploymentData,
                user = loggedUser
              ),
              request.comment
            )
            .map(_.left.map {
              case UnderlyingServiceError(DeploymentService.ConflictingDeploymentIdError(id)) =>
                ConflictingDeploymentIdError(id)
              case UnderlyingServiceError(DeploymentService.ScenarioNotFoundError(scenarioName)) =>
                ScenarioNotFoundError(scenarioName)
              case UnderlyingServiceError(DeploymentService.NoPermissionError) => NoPermissionError
              case UnderlyingServiceError(DeploymentService.ScenarioGraphValidationError(errors)) =>
                ScenarioGraphValidationError(errors)
              case UnderlyingServiceError(DeploymentService.DeployValidationError(message)) =>
                DeployValidationError(message)
              case ActivityService.CommentValidationError(message) => CommentValidationError(message)
            })
        }
      }
  }

  expose {
    endpoints.getDeploymentStatusEndpoint
      .serverSecurityLogic(authorizeKnownUser[GetDeploymentStatusError])
      .serverLogicFlatErrors { implicit loggedUser =>
        { deploymentId =>
          deploymentService
            .getDeploymentStatus(deploymentId)
            .map(_.left.map {
              case DeploymentService.DeploymentNotFoundError(id) => DeploymentNotFoundError(id)
              case DeploymentService.NoPermissionError           => NoPermissionError
            })
        }
      }
  }

}
