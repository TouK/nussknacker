package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.{
  DeploymentCommentError,
  NoPermission,
  NoScenario
}
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.process.repository.CommentValidationError
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.{ExecutionContext, Future}

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    override protected val scenarioService: ProcessService,
    deploymentService: DeploymentService
)(override protected implicit val executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with ScenarioHttpServiceExtensions {

  override protected type BusinessErrorType = DeploymentError
  override protected def noScenarioError(scenarioName: ProcessName): DeploymentError = NoScenario(scenarioName)
  override protected def noPermissionError: NoPermission.type                        = NoPermission

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.requestScenarioDeploymentEndpoint
      .serverSecurityLogic(authorizeKnownUser[DeploymentError])
      .serverLogicEitherT { implicit loggedUser =>
        // FIXME: use deploymentId
        { case (deploymentId, request) =>
          for {
            // TODO reuse fetched scenario inside DeploymentService.deployProcessAsync
            scenarioDetails <- getScenarioWithDetailsByName(request.scenarioName)
            _ <- EitherT.fromEither[Future](
              Either.cond(loggedUser.can(scenarioDetails.processCategory, Permission.Deploy), (), NoPermission)
            )
            _ <- eitherifyErrors(
              deploymentService
                .processCommand(
                  RunDeploymentCommand(
                    scenarioDetails.idWithNameUnsafe,
                    savepointPath = None,
                    comment = request.comment,
                    nodesDeploymentData = request.nodesDeploymentData,
                    user = loggedUser
                  )
                )
            )
          } yield ()
        }
      }
  }

  expose {
    endpoints.checkDeploymentStatusEndpoint
      .serverSecurityLogic(authorizeKnownUser[DeploymentError])
      .serverLogicEitherT { implicit loggedUser =>
        // FIXME: use deploymentId
        { deploymentId =>
          ???
        }
      }
  }

  override protected def handleOtherErrors: PartialFunction[Throwable, DeploymentError] = {
    case CommentValidationError(message) => DeploymentCommentError(message)
  }

}
