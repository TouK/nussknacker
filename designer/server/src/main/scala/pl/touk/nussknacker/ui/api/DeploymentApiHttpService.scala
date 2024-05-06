package pl.touk.nussknacker.ui.api

import cats.implicits.toFunctorOps
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.error.{GetDeploymentStatusError, RunDeploymentError}
import pl.touk.nussknacker.ui.process.deployment.{NewDeploymentService, NewRunDeploymentCommand}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    deploymentService: NewDeploymentService
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
              NewRunDeploymentCommand(
                id = deploymentId,
                scenarioName = request.scenarioName,
                nodesDeploymentData = request.nodesDeploymentData,
                comment = request.comment,
                user = loggedUser
              )
            )
            .map(_.void)
        }
      }
  }

  expose {
    endpoints.getDeploymentStatusEndpoint
      .serverSecurityLogic(authorizeKnownUser[GetDeploymentStatusError])
      .serverLogicFlatErrors { implicit loggedUser =>
        { deploymentId =>
          deploymentService.getDeploymentStatus(deploymentId)
        }
      }
  }

}
