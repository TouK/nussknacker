package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

object conversion extends HideToString {

  @Documentation(description = "Wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive")
  def toAny(@ParamName("value") value: Any): Any = {
    value
  }

  @Documentation(description = "Parse string to number")
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number =
    numeric.toNumber(stringOrNumber)

}
