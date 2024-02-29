package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import sttp.tapir.Schema

@ConfiguredJsonCodec
sealed trait ValueInputWithFixedValues {
  def allowOtherValue: Boolean
  def fixedValuesList: List[FixedExpressionValue]
}

object ValueInputWithFixedValues {
  implicit val schema: Schema[ValueInputWithFixedValues] = Schema.derived[ValueInputWithFixedValues]
}

case class ValueInputWithFixedValuesProvided(fixedValuesList: List[FixedExpressionValue], allowOtherValue: Boolean)
    extends ValueInputWithFixedValues
