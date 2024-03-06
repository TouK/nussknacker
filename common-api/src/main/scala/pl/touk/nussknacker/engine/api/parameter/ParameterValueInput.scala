package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

@ConfiguredJsonCodec
sealed trait ParameterValueInput {
  def allowOtherValue: Boolean
}

case class ValueInputWithFixedValuesProvided(fixedValuesList: List[FixedExpressionValue], allowOtherValue: Boolean)
    extends ParameterValueInput

case class ValueInputWithDictEditor(dictId: String, allowOtherValue: Boolean) extends ParameterValueInput
