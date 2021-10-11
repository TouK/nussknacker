package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.util.MathUtils

object numeric extends MathUtils {

  @Documentation(description = "Parse string to number")
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number = stringOrNumber match {
    case s: CharSequence => new java.math.BigDecimal(s.toString)
    case n: java.lang.Number => n
  }

}
