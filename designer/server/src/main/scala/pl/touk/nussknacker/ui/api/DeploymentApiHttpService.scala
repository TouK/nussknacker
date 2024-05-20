package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    deploymentService: DeploymentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.runDeploymentEndpoint
      .serverSecurityLogic(authorizeKnownUser[RunDeploymentError])
      .serverLogicFlatErrors { implicit loggedUser =>
        { case (deploymentId, request) =>
          deploymentService
            .processCommand(
              RunDeploymentCommand(
                id = deploymentId,
                scenarioName = request.scenarioName,
                nodesDeploymentData = request.nodesDeploymentData,
                comment = request.comment,
                user = loggedUser
              )
            )
            .map(_.left.map {
              case DeploymentService.ConflictingDeploymentIdError(id)     => ConflictingDeploymentIdError(id)
              case DeploymentService.ScenarioNotFoundError(scenarioName)  => ScenarioNotFoundError(scenarioName)
              case DeploymentService.NoPermissionError                    => NoPermissionError
              case DeploymentService.NewCommentValidationError(message)   => CommentValidationError(message)
              case DeploymentService.ScenarioGraphValidationError(errors) => ScenarioGraphValidationError(errors)
              case DeploymentService.DeployValidationError(message)       => DeployValidationError(message)
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
