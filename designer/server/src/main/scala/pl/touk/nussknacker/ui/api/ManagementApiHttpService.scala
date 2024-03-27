package pl.touk.nussknacker.ui.services

import cats.data.{EitherT, Validated, ValidatedNel}
import cats.syntax.all._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints.ManagementApiError
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints.ManagementApiError.{
  NoActionDefinition,
  NoScenario,
  SomethingWentWrong
}
import pl.touk.nussknacker.ui.api.{CustomActionValidationDto, ManagementApiEndpoints}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.util.EitherTImplicits._
import pl.touk.nussknacker.ui.validation.{CustomActionValidationError, CustomActionValidator}

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
      .serverSecurityLogic(authorizeKnownUser[ManagementApiError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            scenarioId <- getScenarioIdByName(processName)
            scenarioIdWithName = ProcessIdWithName(scenarioId, processName)
            actionDefinition <- getActionDefinition(scenarioIdWithName, req.actionName)
            validator = new CustomActionValidator(actionDefinition)
            resultsDto <- validateRequest(validator, req)
          } yield resultsDto
        }
      }
  }

  private def getScenarioIdByName(
      scenarioName: ProcessName
  ): EitherT[Future, NoScenario, ProcessId] = {
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

  private def getActionDefinition(
      processIdWithName: ProcessIdWithName,
      actionName: ScenarioActionName
  )(implicit loggedUser: LoggedUser): EitherT[Future, NoActionDefinition, CustomActionDefinition] = {
    dispatcher
      .deploymentManagerUnsafe(processIdWithName)
      .map(_.customActionsDefinitions.collectFirst { case a @ CustomActionDefinition(`actionName`, _, _, _) => a })
      .toRightEitherT(NoActionDefinition(processIdWithName.name, actionName))
  }

  private def validateRequest(
      validator: CustomActionValidator,
      request: CustomActionRequest
  ): EitherT[Future, ManagementApiError, CustomActionValidationDto] = {
    val validationResult = validator.validateCustomActionParams(request)

    val validationDto = validationResult match {
      case Validated.Valid(_) => CustomActionValidationDto(Nil, validationPerformed = true)
      case Validated.Invalid(errors) => {
        val errorList = errors.toList.map(PrettyValidationErrors.formatErrorMessage(_))
        CustomActionValidationDto(errorList, validationPerformed = true)
      }
    }
    EitherT.rightT[Future, ManagementApiError](validationDto)
  }

}
