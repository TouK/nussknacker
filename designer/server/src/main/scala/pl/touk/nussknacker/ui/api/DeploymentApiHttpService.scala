package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.NoScenario
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.util.EitherTImplicits.EitherTFromOptionInstance

import scala.concurrent.ExecutionContext

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    scenarioService: ProcessService,
    deploymentService: DeploymentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.requestScenarioDeploymentEndpoint
      // FIXME: authorize canDeploy
      .serverSecurityLogic(authorizeKnownUser[DeploymentError])
      .serverLogicEitherT { implicit loggedUser =>
        // TODO: use params
        { case (scenarioName, deploymentId, request) =>
          for {
            scenarioId <- getScenarioIdByName(scenarioName)
            // TODO: Currently it is done sync, but eventually we should make it async and add status checking
            _ <- EitherT.right(
              deploymentService
                .deployProcessAsync(
                  ProcessIdWithName(scenarioId, scenarioName),
                  savepointPath = None,
                  comment = None
                )
                .flatten
            )
          } yield ()
        }
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    scenarioService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

}
