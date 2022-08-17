package pl.touk.nussknacker.engine.util.functions

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, GenericType, MethodTypeInfo, Parameter, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.util.MathUtils
import pl.touk.nussknacker.engine.util.functions.numeric.{AbsTypingFunction, LargeSumTypingFunction, MaxTypingFunction, MinTypingFunction, SumTypingFunction, ToNumberTypingFunction}

trait numeric extends MathUtils {
  @GenericType(typingFunction = classOf[MinTypingFunction])
  override def min(n1: Number, n2: Number): Number = super.min(n1, n2)

  @GenericType(typingFunction = classOf[MaxTypingFunction])
  override def max(n1: Number, n2: Number): Number = super.max(n1, n2)

  @GenericType(typingFunction = classOf[SumTypingFunction])
  override def sum(n1: Number, n2: Number): Number = super.sum(n1, n2)

  @GenericType(typingFunction = classOf[LargeSumTypingFunction])
  override def largeSum(n1: Number, n2: Number): Number = super.largeSum(n1, n2)

  @Documentation(description = "Parse string to number")
  @GenericType(typingFunction = classOf[ToNumberTypingFunction])
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number = stringOrNumber match {
    case s: CharSequence => new java.math.BigDecimal(s.toString)
    case n: java.lang.Number => n
  }

  @Documentation(description = "Returns the value of the first argument raised to the power of the second argument")
  def pow(@ParamName("a") a: Double, @ParamName("b") b: Double): Double = Math.pow(a, b)

  @Documentation(description = "Returns the absolute value of a value.")
  @GenericType(typingFunction = classOf[AbsTypingFunction])
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

object numeric extends numeric {
  private class ToNumberTypingFunction extends TypingFunction {
    override def signatures: Option[NonEmptyList[MethodTypeInfo]] = Some(NonEmptyList.of(
      MethodTypeInfo.withoutVarargs(Parameter("stringOrNumber", Typed(Typed[String], Typed[Number])) :: Nil, Typed[Number])
    ))

    override def computeResultType(arguments: List[typing.TypingResult]): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      Typed[Number].validNel
  }

  private class AbsTypingFunction extends TypingFunction {
    override def computeResultType(arguments: List[typing.TypingResult]): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      arguments.head.withoutValue.validNel
  }

  private trait OperatorTypingFunction extends TypingFunction {
    protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy

    override def computeResultType(arguments: List[typing.TypingResult]): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      numberTypesPromotionStrategy.promote(arguments.head, arguments(1)).withoutValue.validNel
  }

  private class MinTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMinMax
  }

  private class MaxTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMinMax
  }

  private class SumTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMathOperation
  }

  private class LargeSumTypingFunction extends OperatorTypingFunction {
    override protected def numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeNumbersOperation
  }
}
