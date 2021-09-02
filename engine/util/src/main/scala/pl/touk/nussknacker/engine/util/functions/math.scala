package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

object math {

  @Documentation(description = "Returns the smaller of two values.")
  def min(@ParamName("a") a: Number, @ParamName("b") b: Number): Number = Math.min(a.doubleValue(), b.doubleValue())

  @Documentation(description = "Returns the greater of two values.")
  def max(@ParamName("a") a: Number, @ParamName("b") b: Number): Number = Math.max(a.doubleValue(), b.doubleValue())

  @Documentation(description = "Returns the value of the first argument raised to the power of the second argument")
  def pow(@ParamName("a") a: Double, @ParamName("b") b: Double): Double = Math.pow(a, b)

  @Documentation(description = "Returns the absolute value of a value.")
  def abs(@ParamName("a") a: Number): Double = Math.abs(a.doubleValue())

  @Documentation(description = "Returns the largest (closest to positive infinity) value that is less than or equal to the argument and is equal to a mathematical integer.")
  def floor(@ParamName("a") a: Double): Double = Math.floor(a)
}
