package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import cats.syntax.all._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionValidationResult}
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
      // GSK: Here we have some auth validation in the scope of ManagementApiEndpoint/Error
      .serverSecurityLogic(authorizeKnownUser[ManagementApiError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            // GSK: What can go wrong here:
            // 1. fail to fetch scenario id
            scenarioId <- getScenarioIdByName(processName)
            scenarioIdWithName = ProcessIdWithName(scenarioId, processName)
            // 2. fail to fetch action definition
            actionDefinition <- getActionDefinition(scenarioIdWithName, req.actionName)
            validator = new CustomActionValidator(actionDefinition)
            // 3. fail to validate???
            // no validation errors = no fail
            // some validation errors = no fail, technically correct and returning "200 OK"
            // technical exception = fail, should go straight to ERROR 500
            resultsDto <- getValidationsResultDto(validator, req)
          } yield resultsDto
        }
      }
  }

  private def getScenarioIdByName(
      scenarioName: ProcessName
  ): EitherT[Future, NoScenario, ProcessId] = { // GSK: here I am in the scope of ManagementApiError
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

  private def getActionDefinition(
      processIdWithName: ProcessIdWithName,
      actionName: ScenarioActionName
  )(implicit loggedUser: LoggedUser): EitherT[Future, NoActionDefinition, CustomActionDefinition] = { // GSK: same here, the scope of ManagementApiError
    dispatcher
      .deploymentManagerUnsafe(processIdWithName)
      .map(_.customActionsDefinitions.collectFirst { case a @ CustomActionDefinition(actionName, _, _, _) => a })
      .toRightEitherT(NoActionDefinition(processIdWithName.name, actionName))
  }

  private def getValidationsResultDto(
      validator: CustomActionValidator,
      request: CustomActionRequest
  ): EitherT[Future, ManagementApiError, CustomActionValidationDto] = { // GSK: same here, the scope of ManagementApiError
    val validationResult = validator.validateCustomActionParams(request)
    fromValidationResult(validationResult).toEitherT[Future]
  }

  private def fromValidationResult(
      errorOrResult: Either[CustomActionValidationError, CustomActionValidationResult]
  ): Either[ManagementApiError, CustomActionValidationDto] = { // GSK: same here, the scope of ManagementApiError
    def errorList(errorMap: Map[String, List[PartSubGraphCompilationError]]): List[NodeValidationError] = {
      errorMap.values.toList
        .reduce { (a: List[PartSubGraphCompilationError], b: List[PartSubGraphCompilationError]) => a ::: b }
        .map { err: PartSubGraphCompilationError => PrettyValidationErrors.formatErrorMessage(err) }
    }

    // GSK: Too much happens here, can be simplified.
    // This transformation should be inside validator.validateCustomActionParams.
    // Validator should return CustomActionValidationResult only with the list of failing parameters,
    // then we'll avoid this Right(ok) vs Right(not ok) vs Left(not ok) logic.
    errorOrResult
      .map {
        case _ @CustomActionValidationResult.Valid => CustomActionValidationDto(Nil, validationPerformed = true)
        case inv: CustomActionValidationResult.Invalid =>
          CustomActionValidationDto(errorList(inv.errorMap), validationPerformed = true)
      }
      .left
      .map(_ => SomethingWentWrong)
  }

}
