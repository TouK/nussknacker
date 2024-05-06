package pl.touk.nussknacker.ui.error

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.deployment.NewDeploymentId
import sttp.tapir.{Codec, CodecFormat, Mapping}

// The purpose of this class is to hold all business logic errors in one place in order to reduce boilerplate related with rewritting one errors to another and specifying codes for them
// TODO: Rewrite:
//  - all repositories to DB[Either[ClassExtendingDesignerError, X]]
//  - all domain services to Future[Either[ClassExtendingDesignerError, X]]
//  - use subtypes of DesignerError in tapir endpoints as a BUSINESS_ERROR
sealed trait DesignerError

sealed trait RunDeploymentError extends DesignerError

sealed trait BadRequestRunDeploymentError extends RunDeploymentError

sealed trait GetDeploymentStatusError extends DesignerError

final case class ConflictingDeploymentIdError(id: NewDeploymentId) extends RunDeploymentError

final case class ScenarioNotFoundError(scenarioName: ProcessName) extends BadRequestRunDeploymentError

final case class DeploymentNotFoundError(id: NewDeploymentId) extends GetDeploymentStatusError

case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError with CustomAuthorizationError

final case class CommentValidationErrorNG(message: String) extends BadRequestRunDeploymentError

object DesignerError {

  implicit def badRequestRunDeploymentErrorCodec: Codec[String, BadRequestRunDeploymentError, CodecFormat.TextPlain] =
    codec[BadRequestRunDeploymentError] {
      case CommentValidationErrorNG(message)   => message
      case ScenarioNotFoundError(scenarioName) => s"Scenario $scenarioName not found"
    }

  implicit def conflictingDeploymentIdErrorCodec: Codec[String, ConflictingDeploymentIdError, CodecFormat.TextPlain] =
    codec[ConflictingDeploymentIdError](err => s"Deployment with id ${err.id} already exists")

  implicit def deploymentNotFoundErrorCodec: Codec[String, DeploymentNotFoundError, CodecFormat.TextPlain] =
    codec[DeploymentNotFoundError](err => s"Deployment ${err.id} not found")

  private def codec[Error <: DesignerError](
      toMessage: Error => String
  ): Codec[String, Error, CodecFormat.TextPlain] =
    Codec.string.map(
      Mapping.from[String, Error](deserializationNotSupportedException)(toMessage)
    )

  private def deserializationNotSupportedException =
    (_: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

}
