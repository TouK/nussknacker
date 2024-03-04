package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints
import pl.touk.nussknacker.ui.process.deployment.{DeploymentManagerDispatcher, ValidationError}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.validation.CustomActionValidator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ManagementApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    dispatcher: DeploymentManagerDispatcher
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val managementApiEndpoints = new ManagementApiEndpoints(authenticator.authenticationMethod())

  expose {
    managementApiEndpoints.customActionValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[ValidationError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processName, req) =>
          val actionsList = dispatcher.deploymentManagerUnsafe(req.actionName.value).customActionsDefinitions
          val validator   = new CustomActionValidator(actionsList)
          val result: Either[ValidationError, Unit] = Try(validator.validateCustomActionParams(req)) match {
            case util.Success(_)         => Right(())
            case util.Failure(exception) => Left(ValidationError(exception.getMessage))
          }
          EitherT.fromEither[Future](result)
        }
      }
  }

}
