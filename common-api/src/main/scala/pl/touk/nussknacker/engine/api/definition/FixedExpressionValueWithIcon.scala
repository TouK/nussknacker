package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec

@JsonCodec case class FixedExpressionValueWithIcon(expression: String, label: String, icon: String)

object FixedExpressionValueWithIcon {
  val nullFixedValue: FixedExpressionValueWithIcon = FixedExpressionValueWithIcon("", "", "")
}
