package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{CustomActionError, CustomActionResult}

object CustomActionResponse {
  def apply(result: CustomActionResult): CustomActionResponse = CustomActionResponse(isSuccess = true, msg = result.msg)

  def apply(error: CustomActionError): CustomActionResponse = CustomActionResponse(isSuccess = false, msg = error.msg)
}

@JsonCodec
case class CustomActionResponse(isSuccess: Boolean, msg: String)
