package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{CustomActionError, CustomActionResult}

object CustomActionResponse {
  def apply(actionResultE: Either[CustomActionError, CustomActionResult]): CustomActionResponse = {
    actionResultE match {
      case Right(result) => CustomActionResponse(isSuccess = true, msg = result.msg)
      case Left(err) => CustomActionResponse(isSuccess = false, msg = err.msg)
    }
  }
}

@JsonCodec
case class CustomActionResponse(isSuccess: Boolean, msg: String)
