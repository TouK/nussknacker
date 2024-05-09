package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.generics.GenericType
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.util.functions.NumericUtils.ToNumberTypingFunction

object conversion extends ConversionUtils

trait ConversionUtils extends HideToString {

  @Documentation(description =
    "Wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive"
  )
  def toAny(@ParamName("value") value: Any): Any = {
    value
  }

  @Documentation(description = "Parse string to number")
  @GenericType(typingFunction = classOf[ToNumberTypingFunction])
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number =
    numeric.toNumber(stringOrNumber)

}
