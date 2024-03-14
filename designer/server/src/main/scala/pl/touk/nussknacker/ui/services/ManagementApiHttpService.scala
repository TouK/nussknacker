package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos.ScenarioActivityError.NoScenario
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.util.EitherTImplicits.EitherTFromOptionInstance
import pl.touk.nussknacker.ui.validation.{
  CustomActionNonExisting,
  CustomActionValidationResponse,
  CustomActionValidator,
  ValidationError,
  ValidationNotPerformed
}
import sttp.tapir.ValidationResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
      .serverSecurityLogic(authorizeKnownUser[ValidationNotPerformed])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            processIdWithName <- getProcessId(processName)
            actionsList       <- getActionsList(processIdWithName)
          } yield {
            val validator = new CustomActionValidator(actionsList)
            Try(validator.validateCustomActionParams(req)) match {
              case Success(_) => Right(CustomActionValidationResponse.Valid)
              case Failure(ve: ValidationError) =>
                Right(CustomActionValidationResponse.Invalid(s"Validation failed: ${ve.message}"))
              case Failure(na: CustomActionNonExisting) =>
                Left(
                  CustomActionValidationResponse.NotFound(s"Validation failed: couldn't find action ${na.actionName}")
                )
              case Failure(exception) => throw exception
            }
          }
        }
      }
  }

  private def getActionsList(
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser): EitherT[Future, ValidationNotPerformed, List[CustomActionDefinition]] = {
    EitherT.right[ValidationNotPerformed](
      dispatcher.deploymentManagerUnsafe(processIdWithName).map(x => x.customActionsDefinitions)
    )
  }

  private def getProcessId(processName: ProcessName): EitherT[Future, ValidationNotPerformed, ProcessIdWithName] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
    } yield ProcessIdWithName(scenarioId, processName)
  }

  private def getScenarioIdByName(scenarioName: ProcessName): EitherT[Future, ValidationNotPerformed, ProcessId] = {
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(NotFound("Can't find scenario id for scenario: " + scenarioName))
  }

}
