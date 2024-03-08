package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos.ScenarioActivityError.NoScenario
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.{DeploymentManagerDispatcher, ValidationError}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.util.EitherTImplicits.EitherTFromOptionInstance
import pl.touk.nussknacker.ui.validation.CustomActionValidator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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
      .serverSecurityLogic(authorizeKnownUser[ValidationError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          for {
            processIdWithName <- getProcessId(processName)
            actionsList <- EitherT.fromOption[Future](
              dispatcher.deploymentManagerUnsafe(processIdWithName).map(x => x.customActionsDefinitions),
              Nil
            )
            validator = new CustomActionValidator(actionsList)
            validationResult <- Try(validator.validateCustomActionParams(req)) match {
              case util.Success(_)         => Right(())
              case util.Failure(exception) => Left(ValidationError(exception.getMessage))
            }
          } yield validationResult
        /* val actionsList = dispatcher.deploymentManagerUnsafe(getProcessId(processName)).customActionsDefinitions
          val validator   = new CustomActionValidator(actionsList)
          val result: Either[ValidationError, Unit] = Try(validator.validateCustomActionParams(req)) match {
            case util.Success(_)         => Right(())
            case util.Failure(exception) => Left(ValidationError(exception.getMessage))
          }
          EitherT.fromEither[Future](result)*/
        }
      }
  }

  private def getProcessId(processName: ProcessName): EitherT[Future, NoScenario, ProcessIdWithName] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
    } yield ProcessIdWithName(scenarioId, processName)
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    processService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

}
