package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec

@JsonCodec case class FixedExpressionValue(expression: String, label: String, hintText: Option[String] = None)

object FixedExpressionValue {
  def apply(expression: String, label: String): FixedExpressionValue = FixedExpressionValue(expression, label, None)
  val nullFixedValue: FixedExpressionValue                           = FixedExpressionValue("", "")
}
