package pl.touk.nussknacker.ui.error

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentIdNG
import sttp.tapir.{Codec, CodecFormat, Mapping}

// The purpose of this class is to hold all business logic errors in one place in order to reduce boilerplate related with rewritting one errors to another and specifying codes for them
// TODO: Rewrite:
//  - all repositories to EitherT[DB, ClassExtendingDesignerError, X]
//  - all domain services to EitherT[Future, ClassExtendingDesignerError, X]
//  - use subtypes of DesignerError in tapir endpoints as a BUSINESS_ERROR
sealed trait DesignerError

sealed trait RunDeploymentError extends DesignerError

sealed trait GetDeploymentStatusError extends DesignerError

final case class ScenarioNotFoundError(scenarioName: ProcessName) extends RunDeploymentError

final case class DeploymentNotFoundError(id: DeploymentIdNG) extends GetDeploymentStatusError

case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError with CustomAuthorizationError

final case class CommentValidationErrorNG(message: String) extends RunDeploymentError

object DesignerError {

  implicit def scenarioNotFoundErrorCodec: Codec[String, ScenarioNotFoundError, CodecFormat.TextPlain] =
    codec[ScenarioNotFoundError](err => s"Scenario ${err.scenarioName} not found")

  implicit def commentValidationErrorCodec: Codec[String, CommentValidationErrorNG, CodecFormat.TextPlain] =
    codec[CommentValidationErrorNG](_.message)

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
