package pl.touk.nussknacker.ui.api

import cats.data.{EitherT, Validated}
import cats.syntax.all._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.api.description.ManagementApiEndpoints
import pl.touk.nussknacker.ui.api.description.ManagementApiEndpoints.Dtos.{
  CustomActionValidationDto,
  ManagementApiError,
  NoActionDefinition,
  NoScenario
}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.validation.CustomActionValidator

import scala.concurrent.{ExecutionContext, Future}

class ManagementApiHttpService(
    authManager: AuthManager,
    dispatcher: DeploymentManagerDispatcher,
    processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val managementApiEndpoints = new ManagementApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    managementApiEndpoints.customActionValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[ManagementApiError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            scenarioId <- getScenarioIdByName(processName)
            scenarioIdWithName = ProcessIdWithName(scenarioId, processName)
            actionDefinition <- getActionDefinition(scenarioIdWithName, req.actionName)
            validator = new CustomActionValidator(actionDefinition)
            resultsDto <- getValidationResultsDto(validator, req)
          } yield resultsDto
        }
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName): EitherT[Future, NoScenario, ProcessId] = {
    EitherT.fromOptionF(
      processService.getProcessId(scenarioName),
      NoScenario(scenarioName)
    )
  }

  private def getActionDefinition(
      processIdWithName: ProcessIdWithName,
      actionName: ScenarioActionName
  )(implicit loggedUser: LoggedUser): EitherT[Future, NoActionDefinition, CustomActionDefinition] = {
    EitherT.fromOptionF(
      dispatcher
        .deploymentManagerUnsafe(processIdWithName)
        .map(_.customActionsDefinitions.collectFirst { case a @ CustomActionDefinition(`actionName`, _, _, _) => a }),
      NoActionDefinition(processIdWithName.name, actionName)
    )
  }

  private def getValidationResultsDto(
      validator: CustomActionValidator,
      request: CustomActionRequest
  ): EitherT[Future, ManagementApiError, CustomActionValidationDto] = {
    val validationResult = validator.validateCustomActionParams(request.params)

    val validationDto = validationResult match {
      case Validated.Valid(_) => CustomActionValidationDto(Nil, validationPerformed = true)
      case Validated.Invalid(errors) =>
        val errorList = errors.toList.map(PrettyValidationErrors.formatErrorMessage(_))
        CustomActionValidationDto(errorList, validationPerformed = true)
    }
    EitherT.rightT[Future, ManagementApiError](validationDto)
  }

}
