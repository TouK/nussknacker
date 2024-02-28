package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

@ConfiguredJsonCodec
sealed trait FragmentParameterValueInput {
  def allowOtherValue: Boolean
}

case class ValueInputWithFixedValuesProvided(fixedValuesList: List[FixedExpressionValue], allowOtherValue: Boolean)
    extends FragmentParameterValueInput

case class ValueInputWithDictEditor(dictId: String, allowOtherValue: Boolean) extends FragmentParameterValueInput
