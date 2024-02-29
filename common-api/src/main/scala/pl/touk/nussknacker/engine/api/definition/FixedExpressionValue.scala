package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec
import sttp.tapir.Schema

@JsonCodec case class FixedExpressionValue(expression: String, label: String)

object FixedExpressionValue {
  val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  implicit val schema: Schema[FixedExpressionValue] = Schema.derived[FixedExpressionValue]
}
