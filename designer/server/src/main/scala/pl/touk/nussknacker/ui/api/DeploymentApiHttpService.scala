package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.{NoPermission, NoScenario}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DeploymentApiHttpService(
    authenticator: AuthenticationResources,
    scenarioService: ProcessService,
    deploymentService: DeploymentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new DeploymentApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.requestScenarioDeploymentEndpoint
      .serverSecurityLogic(authorizeKnownUser[DeploymentError])
      .serverLogicEitherT { implicit loggedUser =>
        // TODO (next PRs): use params
        { case (scenarioName, deploymentId, request) =>
          for {
            scenarioDetails <- getScenarioDetailsByName(scenarioName)
            _ <- EitherT.fromEither[Future](
              Either.cond(loggedUser.can(scenarioDetails.processCategory, Permission.Deploy), (), NoPermission)
            )
            // TODO (next PRs): Currently it is done sync, but eventually we should make it async and add an endpoint for deployment status verification
            _ <- extractErrors(
              deploymentService
                .deployProcessAsync(
                  scenarioDetails.idWithNameUnsafe,
                  savepointPath = None,
                  comment = None
                )
                .flatten
            )
          } yield ()
        }
      }
  }

  // TODO: It is a copy-paste of akka-based AuthorizeProcess variant. We should:
  //       - reuse fetched scenario inside DeploymentService.deployProcessAsync
  //       - extract this boilerplate for permission checking to some class - but before that we should rethink errors
  //         type hierarchy. currently we have dedicated errors for each endpoint which makes this hard to achieve
  private def getScenarioDetailsByName(
      scenarioName: ProcessName
  )(implicit loggedUser: LoggedUser): EitherT[Future, DeploymentError, ScenarioWithDetails] =
    for {
      scenarioId <- EitherT.fromOptionF(scenarioService.getProcessId(scenarioName), NoScenario(scenarioName))
      scenarioDetails <- extractErrors(
        scenarioService.getLatestProcessWithDetails(
          ProcessIdWithName(scenarioId, scenarioName),
          GetScenarioWithDetailsOptions.detailsOnly
        )
      )
    } yield scenarioDetails

  private def extractErrors[T](future: Future[T]): EitherT[Future, DeploymentError, T] = {
    EitherT(
      future.transform {
        case Success(result)               => Success(Right(result))
        case Failure(_: UnauthorizedError) => Success(Left(NoPermission))
        case Failure(ex)                   => Failure(ex)
      }
    )
  }

}
