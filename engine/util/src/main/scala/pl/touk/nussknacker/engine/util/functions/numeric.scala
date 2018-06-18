package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

object numeric {

  @Documentation(description = "Parse string to number")
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number = stringOrNumber match {
    case s: String => new java.math.BigDecimal(s)
    case n: java.lang.Number => n
  }



}
