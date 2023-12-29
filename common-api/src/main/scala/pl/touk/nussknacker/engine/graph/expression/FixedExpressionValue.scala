package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec

@JsonCodec case class FixedExpressionValue(expression: String, label: String)

object FixedExpressionValue {
  val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")
}
