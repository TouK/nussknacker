package pl.touk.nussknacker.engine.util.functions

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.api.generics.{
  GenericFunctionTypingError,
  GenericType,
  MethodTypeInfo,
  Parameter,
  TypingFunction
}
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.MathUtils
import pl.touk.nussknacker.engine.util.functions.NumericUtils.{
  LargeNumberOperatorTypingFunction,
  MathOperatorTypingFunction,
  MinMaxTypingFunction,
  SingleArgumentMathTypingFunction,
  ToNumberTypingFunction
}

import scala.collection.compat.immutable.LazyList
import scala.util.{Success, Try}

object numeric extends NumericUtils

trait NumericUtils extends MathUtils with HideToString {
  @GenericType(typingFunction = classOf[MinMaxTypingFunction])
  override def min(n1: Number, n2: Number): Number = super.min(n1, n2)

  @GenericType(typingFunction = classOf[MinMaxTypingFunction])
  override def max(n1: Number, n2: Number): Number = super.max(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def sum(n1: Number, n2: Number): Number = super.sum(n1, n2)

  @GenericType(typingFunction = classOf[LargeNumberOperatorTypingFunction])
  override def largeSum(n1: Number, n2: Number): Number = super.largeSum(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def plus(n1: Number, n2: Number): Number = super.plus(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def minus(n1: Number, n2: Number): Number = super.minus(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def multiply(n1: Number, n2: Number): Number = super.multiply(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def divide(n1: Number, n2: Number): Number = super.divide(n1, n2)

  @GenericType(typingFunction = classOf[MathOperatorTypingFunction])
  override def remainder(n1: Number, n2: Number): Number = super.remainder(n1, n2)

  @GenericType(typingFunction = classOf[SingleArgumentMathTypingFunction])
  override def negate(n1: Number): Number = super.negate(n1)

  @Documentation(description = "Parse string to number")
  @GenericType(typingFunction = classOf[ToNumberTypingFunction])
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number = stringOrNumber match {
    case s: CharSequence =>
      val ss = s.toString
      // we pick the narrowest type as possible to reduce the amount of memory and computations overheads
      val tries: LazyList[Try[java.lang.Number]] = LazyList(
        Try(java.lang.Integer.parseInt(ss)),
        Try(java.lang.Long.parseLong(ss)),
        Try(java.lang.Double.parseDouble(ss)),
        Try(new java.math.BigDecimal(ss)),
        Try(new java.math.BigInteger(ss))
      )

      tries
        .collectFirst { case Success(value) =>
          value
        }
        .getOrElse(new java.math.BigDecimal(ss))

    case n: java.lang.Number => n
  }

  @Documentation(description = "Returns the value of the first argument raised to the power of the second argument")
  def pow(@ParamName("a") a: Double, @ParamName("b") b: Double): Double = Math.pow(a, b)

  @Documentation(description = "Returns the absolute value of a value.")
  @GenericType(typingFunction = classOf[SingleArgumentMathTypingFunction])
  def abs(@ParamName("a") a: Number): Number = a match {
    case n: java.lang.Byte       => Math.abs(n.intValue())
    case n: java.lang.Short      => Math.abs(n.intValue())
    case n: java.lang.Integer    => Math.abs(n)
    case n: java.lang.Long       => Math.abs(n)
    case n: java.lang.Float      => Math.abs(n)
    case n: java.lang.Double     => Math.abs(n)
    case n: java.math.BigDecimal => n.abs()
    case n: java.math.BigInteger => n.abs()
  }

  @Documentation(description =
    "Returns the largest (closest to positive infinity) value that is less than or equal to the argument and is equal to a mathematical integer."
  )
  def floor(@ParamName("a") a: java.lang.Double): java.lang.Double = Math.floor(a)

  @Documentation(description =
    "Returns the closest long to the argument. The result is rounded to an integer by adding 1/2, taking the floor of the result, and casting the result to type long."
  )
  def round(@ParamName("a") a: java.lang.Double): java.lang.Double = Math.round(a).toDouble

  @Documentation(description =
    "Returns the smallest (closest to negative infinity) double value that is greater than or equal to the argument and is equal to a mathematical integer."
  )
  def ceil(@ParamName("a") a: java.lang.Double): java.lang.Double = Math.ceil(a)

  @Documentation(description = "Sine of a value (argument in radians).")
  def sin(@ParamName("a") a: Number): java.lang.Double = Math.sin(a.doubleValue())

  @Documentation(description = "Cosine of a value (argument in radians).")
  def cos(@ParamName("a") a: Number): java.lang.Double = Math.cos(a.doubleValue())

  @Documentation(description = "Tangent of a value (argument in radians).")
  def tan(@ParamName("a") a: Number): java.lang.Double = Math.tan(a.doubleValue())

  @Documentation(description = "Arc sine (result in radians).")
  def asin(@ParamName("a") a: Number): java.lang.Double = Math.asin(a.doubleValue())

  @Documentation(description = "Arc cosine (result in radians).")
  def acos(@ParamName("a") a: Number): java.lang.Double = Math.acos(a.doubleValue())

  @Documentation(description = "Arc tangent (result in radians).")
  def atan(@ParamName("a") a: Number): java.lang.Double = Math.atan(a.doubleValue())

  @Documentation(description = "Square root.")
  def sqrt(@ParamName("a") a: Number): java.lang.Double = Math.sqrt(a.doubleValue())

  @Documentation(description = "Cubic root.")
  def cbrt(@ParamName("a") a: Number): java.lang.Double = Math.cbrt(a.doubleValue())

  @Documentation(description = "Exponential function e^a.")
  def exp(@ParamName("a") a: Number): java.lang.Double = Math.exp(a.doubleValue())

  @Documentation(description = "Natural logarithm (base e).")
  def log(@ParamName("a") a: Number): java.lang.Double = Math.log(a.doubleValue())

  @Documentation(description = "Base-10 logarithm.")
  def log10(@ParamName("a") a: Number): java.lang.Double = Math.log10(a.doubleValue())

  @Documentation(description = "Converts degrees to radians.")
  def toRadians(@ParamName("a") a: Number): java.lang.Double = Math.toRadians(a.doubleValue())

  @Documentation(description = "Converts radians to degrees.")
  def toDegrees(@ParamName("a") a: Number): java.lang.Double = Math.toDegrees(a.doubleValue())

}

object NumericUtils {

  class ToNumberTypingFunction extends TypingFunction {

    override def signatures: Option[NonEmptyList[MethodTypeInfo]] = Some(
      NonEmptyList.of(
        MethodTypeInfo.withoutVarargs(
          Parameter("stringOrNumber", Typed(Typed[String], Typed[Number])) :: Nil,
          Typed[Number]
        )
      )
    )

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = {
      if (arguments.head.canBeLooselyAssignedTo(Typed[Number])) arguments.head.withoutValue.validNel
      else Typed[Number].validNel
    }

  }

  private class SingleArgumentMathTypingFunction extends TypingFunction {

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      arguments.head.withoutValue.validNel

  }

  private trait OperatorTypingFunction extends TypingFunction {
    protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      numberTypesPromotionStrategy.promote(arguments.head, arguments(1)).withoutValue.validNel

  }

  private class MinMaxTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMinMax
  }

  private class MathOperatorTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMathOperation
  }

  private class LargeNumberOperatorTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeNumbersOperation
  }

  private class LargeFloatingNumberOperatorTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeFloatingNumbersOperation
  }

}
