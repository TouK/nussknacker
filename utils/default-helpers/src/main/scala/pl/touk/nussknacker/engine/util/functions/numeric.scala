package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.util.MathUtils

trait numeric extends MathUtils {

  @Documentation(description = "Parse string to number")
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number = stringOrNumber match {
    case s: CharSequence => new java.math.BigDecimal(s.toString)
    case n: java.lang.Number => n
  }

  @Documentation(description = "Returns the value of the first argument raised to the power of the second argument")
  def pow(@ParamName("a") a: Double, @ParamName("b") b: Double): Double = Math.pow(a, b)

  @Documentation(description = "Returns the absolute value of a value.")
  def abs(@ParamName("a") a: Number): Number = a match {
    case n: java.lang.Byte => Math.abs(n.intValue())
    case n: java.lang.Short => Math.abs(n.intValue())
    case n: java.lang.Integer => Math.abs(n)
    case n: java.lang.Long => Math.abs(n)
    case n: java.lang.Float => Math.abs(n)
    case n: java.lang.Double => Math.abs(n)
    case n: java.math.BigDecimal => n.abs()
    case n: java.math.BigInteger => n.abs()
  }

  @Documentation(description = "Returns the largest (closest to positive infinity) value that is less than or equal to the argument and is equal to a mathematical integer.")
  def floor(@ParamName("a") a: Double): Double = Math.floor(a)

  @Documentation(description = "Returns the closest long to the argument. The result is rounded to an integer by adding 1/2, taking the floor of the result, and casting the result to type long.")
  def round(@ParamName("a") a: Double): Double = Math.round(a)

  @Documentation(description = "Returns the smallest (closest to negative infinity) double value that is greater than or equal to the argument and is equal to a mathematical integer.")
  def ceil(@ParamName("a") a: Double): Double = Math.ceil(a)

}

object numeric extends numeric
