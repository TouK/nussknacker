package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.error.{GetDeploymentStatusError, RunDeploymentError}
import pl.touk.nussknacker.ui.process.deployment.RunDeploymentCommandNG
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentServiceNG
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    deploymentService: DeploymentServiceNG
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.runDeploymentEndpoint
      .serverSecurityLogic(authorizeKnownUser[RunDeploymentError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (deploymentId, request) =>
          deploymentService
            .processCommand(
              RunDeploymentCommandNG(
                id = deploymentId,
                scenarioName = request.scenarioName,
                nodesDeploymentData = request.nodesDeploymentData,
                comment = request.comment,
                user = loggedUser
              )
            )
            .map(_.map(_ => ()))
        }
      }
  }

  expose {
    endpoints.getDeploymentStatusEndpoint
      .serverSecurityLogic(authorizeKnownUser[GetDeploymentStatusError])
      .serverLogicEitherT { implicit loggedUser =>
        { deploymentId =>
          deploymentService.getDeploymentStatus(deploymentId)
        }
      }
  }

}
