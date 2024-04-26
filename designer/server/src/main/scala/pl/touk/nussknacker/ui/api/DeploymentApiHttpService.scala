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
import pl.touk.nussknacker.ui.process.deployment.{CommonCommandData, DeploymentService, RunDeploymentCommand}
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
        // TODO (next PRs): use params
        { case (scenarioName, deploymentId, request) =>
          for {
            // TODO (next PRs) reuse fetched scenario inside DeploymentService.deployProcessAsync
            scenarioDetails <- getScenarioWithDetailsByName(scenarioName)
            _ <- EitherT.fromEither[Future](
              Either.cond(loggedUser.can(scenarioDetails.processCategory, Permission.Deploy), (), NoPermission)
            )
            // TODO (next PRs): Currently it is done sync, but eventually we should make it async and add an endpoint for deployment status verification
            _ <- eitherifyErrors(
              deploymentService
                .processCommand(
                  RunDeploymentCommand(
                    commonData = CommonCommandData(scenarioDetails.idWithNameUnsafe, request.comment, loggedUser),
                    savepointPath = None,
                    nodesDeploymentData = request.nodesDeploymentData,
                  )
                )
                .flatten
            )
          } yield ()
        }
      }
  }

  override protected def handleOtherErrors: PartialFunction[Throwable, DeploymentError] = {
    case CommentValidationError(message) => DeploymentCommentError(message)
  }

}
