package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec

@JsonCodec case class FixedExpressionValue(expression: String, label: String)

object FixedExpressionValue {
  val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")
}
