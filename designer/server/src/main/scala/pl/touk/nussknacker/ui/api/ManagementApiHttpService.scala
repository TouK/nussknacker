package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import cats.syntax.all._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionValidationResult}
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.{CustomActionValidationDto, ManagementApiEndpoints}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.util.EitherTImplicits.{EitherTFromMonad, EitherTFromOptionInstance}
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
      .serverSecurityLogic(authorizeKnownUser[CustomActionValidationError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            processIdWithName <- getProcessId(processName)
            actionsList       <- getActionsList(processIdWithName)
            resultsDto        <- getValidationsResultDto(actionsList, req)
          } yield resultsDto
        }
      }
  }

  private def getProcessId(
      processName: ProcessName
  ): EitherT[Future, CustomActionValidationError, ProcessIdWithName] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
    } yield ProcessIdWithName(scenarioId, processName)
  }

  private def getScenarioIdByName(
      scenarioName: ProcessName
  ): EitherT[Future, CustomActionValidationError, ProcessId] = {
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(CustomActionValidationError(scenarioName.value))
  }

  private def getActionsList(
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser): EitherT[Future, CustomActionValidationError, List[CustomActionDefinition]] = {
    EitherT.right[CustomActionValidationError](
      dispatcher.deploymentManagerUnsafe(processIdWithName).map(x => x.customActionsDefinitions)
    )
  }

  private def getValidationsResultDto(
      actionList: List[CustomActionDefinition],
      request: CustomActionRequest
  ): EitherT[Future, CustomActionValidationError, CustomActionValidationDto] = {
    val validator        = new CustomActionValidator(actionList)
    val validationResult = validator.validateCustomActionParams(request)
    fromValidationResult(validationResult).toEitherT[Future]
  }

  private def fromValidationResult(
      errorOrResult: Either[CustomActionValidationError, CustomActionValidationResult]
  ): Either[CustomActionValidationError, CustomActionValidationDto] = {
    def errorList(errorMap: Map[String, List[PartSubGraphCompilationError]]): List[NodeValidationError] = {
      errorMap.values.toList
        .reduce { (a: List[PartSubGraphCompilationError], b: List[PartSubGraphCompilationError]) => a ::: b }
        .map { err: PartSubGraphCompilationError => PrettyValidationErrors.formatErrorMessage(err) }
    }

    errorOrResult match {
      case Right(v: CustomActionValidationResult.Valid.type) =>
        CustomActionValidationDto(Nil, validationPerformed = true).asRight[CustomActionValidationError]
      case Right(inv: CustomActionValidationResult.Invalid) =>
        CustomActionValidationDto(errorList(inv.errorMap), validationPerformed = true)
          .asRight[CustomActionValidationError]
      case Left(exc) => exc.asLeft[CustomActionValidationDto]
    }
  }

}
