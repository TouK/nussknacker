package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.{DeploymentError, nodesDeploymentDataCodec}
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.{NoPermission, NoScenario}
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService, RunDeploymentCommand}
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
                    scenarioDetails.idWithNameUnsafe,
                    savepointPath = None,
                    comment = None,
                    nodesDeploymentData = request.nodesDeploymentData,
                    user = loggedUser
                  )
                )
                .flatten
            )
          } yield ()
        }
      }
  }

}
