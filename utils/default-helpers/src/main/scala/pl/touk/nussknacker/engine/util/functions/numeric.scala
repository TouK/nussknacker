package pl.touk.nussknacker.engine.util.functions

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
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
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.util.MathUtils
import pl.touk.nussknacker.engine.util.functions.numeric.{
  LargeFloatingNumberOperatorTypingFunction,
  LargeNumberOperatorTypingFunction,
  MathOperatorTypingFunction,
  MinMaxTypingFunction,
  SingleArgumentMathTypingFunction,
  ToNumberTypingFunction
}

import scala.util.{Success, Try}

trait numeric extends MathUtils with HideToString {
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
      val ss                                       = s.toString
      val tryByte: Try[java.lang.Byte]             = Try(java.lang.Byte.parseByte(ss))
      val tryShort: Try[java.lang.Short]           = Try(java.lang.Short.parseShort(ss))
      val tryInteger: Try[java.lang.Integer]       = Try(java.lang.Integer.parseInt(ss))
      val tryLong: Try[java.lang.Long]             = Try(java.lang.Long.parseLong(ss))
      val tryFloat: Try[java.lang.Float]           = Try(java.lang.Float.parseFloat(ss))
      val tryDouble: Try[java.lang.Double]         = Try(java.lang.Double.parseDouble(ss))
      val tryBigDecimal: Try[java.math.BigDecimal] = Try(new java.math.BigDecimal(ss))
      val tryBigInteger: Try[java.math.BigInteger] = Try(new java.math.BigInteger(ss))

      val tries: List[Try[java.lang.Number]] =
        List(tryByte, tryShort, tryInteger, tryLong, tryFloat, tryDouble, tryBigDecimal, tryBigInteger)

      val firstSuccess: Option[Number] = tries
        .collectFirst { case Success(value) =>
          value
        }
        .orElse(Some(new java.math.BigDecimal(ss)))

      firstSuccess.get
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
  def floor(@ParamName("a") a: Double): Double = Math.floor(a)

  @Documentation(description =
    "Returns the closest long to the argument. The result is rounded to an integer by adding 1/2, taking the floor of the result, and casting the result to type long."
  )
  def round(@ParamName("a") a: Double): Double = Math.round(a).toDouble

  @Documentation(description =
    "Returns the smallest (closest to negative infinity) double value that is greater than or equal to the argument and is equal to a mathematical integer."
  )
  def ceil(@ParamName("a") a: Double): Double = Math.ceil(a)

}

object numeric extends numeric {

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
      if (arguments.head.canBeSubclassOf(Typed[Number])) arguments.head.withoutValue.validNel
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
