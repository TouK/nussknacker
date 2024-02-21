package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ActionResult

object CustomActionResponse {

  def apply(actionResult: ActionResult): CustomActionResponse =
    CustomActionResponse(isSuccess = true, msg = actionResult.msg)

}

@JsonCodec
final case class CustomActionResponse(isSuccess: Boolean, msg: String)
