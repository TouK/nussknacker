package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.ProblemDeploymentStatus
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.newactivity.ActivityService
import pl.touk.nussknacker.ui.process.newactivity.ActivityService.UnderlyingServiceError
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.security.api.AuthManager

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authManager: AuthManager,
    activityService: ActivityService,
    deploymentService: DeploymentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager) {

  private val endpoints = new DeploymentApiEndpoints(authManager.authenticationEndpointInput())

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
                nodesDeploymentData = NodesDeploymentData(request.nodesDeploymentData.map { case (nodeId, paramValue) =>
                  (nodeId, Map("sqlExpression" -> paramValue))
                }),
                user = loggedUser
              ),
              request.comment.flatMap(Comment.from)
            )
            .map(_.left.map {
              case UnderlyingServiceError(err) =>
                err match {
                  case DeploymentService.ConflictingDeploymentIdError(id) =>
                    ConflictingDeploymentIdError(id)
                  case DeploymentService
                        .ConcurrentDeploymentsForScenarioArePerformedError(scenarioName, concurrentDeploymentsIds) =>
                    ConcurrentDeploymentsForScenarioArePerformedError(scenarioName, concurrentDeploymentsIds)
                  case DeploymentService.ScenarioNotFoundError(scenarioName) =>
                    ScenarioNotFoundError(scenarioName)
                  case DeploymentService.DeploymentOfFragmentError =>
                    DeploymentOfFragmentError
                  case DeploymentService.DeploymentOfArchivedScenarioError =>
                    DeploymentOfArchivedScenarioError
                  case DeploymentService.NoPermissionError => NoPermissionError
                  case DeploymentService.ScenarioGraphValidationError(errors) =>
                    ScenarioGraphValidationError(errors)
                  case DeploymentService.DeployValidationError(message) =>
                    DeployValidationError(message)
                }
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
            .map(
              _.map { statusWithModifiedAt =>
                GetDeploymentStatusResponse(
                  statusWithModifiedAt.value.name,
                  ProblemDeploymentStatus.extractDescription(statusWithModifiedAt.value),
                  statusWithModifiedAt.modifiedAt.toInstant
                )
              }.left.map {
                case DeploymentService.DeploymentNotFoundError(id) => DeploymentNotFoundError(id)
                case DeploymentService.NoPermissionError           => NoPermissionError
              }
            )
        }
      }
  }

}
