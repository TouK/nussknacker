package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionValidationResult}
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.util.EitherTImplicits.{EitherTFromMonad, EitherTFromOptionInstance}
import pl.touk.nussknacker.ui.validation.CustomActionValidator

import scala.concurrent.{ExecutionContext, Future}

class ManagementApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    dispatcher: DeploymentManagerDispatcher,
    processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val managementApiEndpoints = new ManagementApiEndpoints(authenticator.authenticationMethod())

  expose {
    managementApiEndpoints.customActionValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            processIdWithName <- getProcessId(processName)
            actionsList       <- getActionsList(processIdWithName)
            validator = new CustomActionValidator(actionsList)
            validationResult <- EitherT.fromEither[Future](validator.validateCustomActionParams(req))
          } yield validationResult
        }
      }
  }

  private def getProcessId(processName: ProcessName): EitherT[Future, NuDesignerError, ProcessIdWithName] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
    } yield ProcessIdWithName(scenarioId, processName)
  }

  private def getScenarioIdByName(scenarioName: ProcessName): EitherT[Future, NuDesignerError, ProcessId] = {
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(ProcessNotFoundError(scenarioName))
  }

  private def getActionsList(
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser): EitherT[Future, NuDesignerError, List[CustomActionDefinition]] = {
    EitherT.right[NuDesignerError](
      dispatcher.deploymentManagerUnsafe(processIdWithName).map(x => x.customActionsDefinitions)
    )
  }

}
