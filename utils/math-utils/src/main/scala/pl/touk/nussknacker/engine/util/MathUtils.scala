package pl.touk.nussknacker.engine.util

import org.springframework.util.{NumberUtils => SpringNumberUtils}
import pl.touk.nussknacker.engine.api.Hidden
import pl.touk.nussknacker.engine.api.typed.supertype.{
  NumberTypesPromotionStrategy,
  ReturningSingleClassPromotionStrategy
}

import java.lang
import java.math.MathContext
import java.math.RoundingMode
import javax.annotation.Nullable

trait MathUtils {

  def min(@Nullable n1: Number, @Nullable n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerReturningNumber {
        override def onBytes(n1: java.lang.Byte, n2: java.lang.Byte): java.lang.Byte =
          Math.min(n1.intValue(), n2.intValue()).byteValue()
        override def onShorts(n1: java.lang.Short, n2: java.lang.Short): java.lang.Short =
          Math.min(n1.intValue(), n2.intValue()).shortValue()
        override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer =
          Math.min(n1, n2)
        override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long =
          Math.min(n1, n2)
        override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
          n1.min(n2)
        override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float =
          Math.min(n1, n2)
        override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double =
          Math.min(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
          n1.min(n2)
      })
    }
  }

  def max(@Nullable n1: Number, @Nullable n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerReturningNumber {
        override def onBytes(n1: java.lang.Byte, n2: java.lang.Byte): java.lang.Byte =
          Math.max(n1.intValue(), n2.intValue()).byteValue()
        override def onShorts(n1: java.lang.Short, n2: java.lang.Short): java.lang.Short =
          Math.max(n1.intValue(), n2.intValue()).shortValue()
        override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer =
          Math.max(n1, n2)
        override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long =
          Math.max(n1, n2)
        override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
          n1.max(n2)
        override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float =
          Math.max(n1, n2)
        override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double =
          Math.max(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
          n1.max(n2)
      })
    }
  }

  def sum(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy =
      NumberTypesPromotionStrategy.ForMathOperation
    promoteThenSum(n1, n2)
  }

  def largeSum(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeNumbersOperation
    promoteThenSum(n1, n2)
  }

  @Hidden
  def largeFloatingSum(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeFloatingNumbersOperation
    promoteThenSum(n1, n2)
  }

  @Hidden
  def largeFloatSquare(number: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeFloatingNumbersOperation
    val converted = convertToPromotedType(number)
    multiply(converted, converted)
  }

  @Hidden
  def largeFloatSqrt(number: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeFloatingNumbersOperation

    val converted = convertToPromotedType(number)

    converted match {
      case converted: java.lang.Double     => Math.sqrt(converted)
      case converted: java.math.BigDecimal => converted.sqrt(MathContext.DECIMAL128)
    }
  }

  def plus(n1: Number, n2: Number): Number = sum(n1, n2)

  def minus(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerForPromotingMathOp {
      override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer = n1 - n2
      override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long         = n1 - n2
      override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
        n1.subtract(n2)
      override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float     = n1 - n2
      override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double = n1 - n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
        n1.subtract(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def multiply(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerForPromotingMathOp {
      override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer = n1 * n2
      override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long         = n1 * n2
      override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
        n1.multiply(n2)
      override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float     = n1 * n2
      override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double = n1 * n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
        n1.multiply(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  // divide method has peculiar behaviour when it comes to BigDecimals (see its implementation), hence this method is sometimes needed
  @Hidden
  def divideWithDefaultBigDecimalScale(n1: Number, n2: Number): Number = {
    if (n1.isInstanceOf[java.math.BigDecimal] || n2.isInstanceOf[java.math.BigDecimal]) {
      (BigDecimal(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.math.BigDecimal]))
        /
          BigDecimal(SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.math.BigDecimal]))).bigDecimal
    } else {
      divide(n1, n2)
    }
  }

  def divide(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerForPromotingMathOp {
      override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer = n1 / n2
      override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long         = n1 / n2
      override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
        n1.divide(n2)
      override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float     = n1 / n2
      override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double = n1 / n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal = {
        n1.divide(
          n2,
          // This is copied behaviour of divide operation in spel (class OpDivide) but it can lead to issues when both big decimals have small scales.
          // Small scales happen when integer is converted to BigDecimal using SpringNumberUtils.convertNumberToTargetClass
          Math.max(n1.scale(), n2.scale),
          RoundingMode.HALF_EVEN
        ) // same scale and rounding as used by OpDivide in SpelExpression.java
      }
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def remainder(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerForPromotingMathOp {
      override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer = n1 % n2
      override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long         = n1 % n2
      override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
        n1.remainder(n2)
      override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float     = n1 % n2
      override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double = n1 % n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
        n1.remainder(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def negate(n1: Number): Number = n1 match {
    case n1: java.lang.Byte       => -n1
    case n1: java.lang.Short      => -n1
    case n1: java.lang.Integer    => -n1
    case n1: java.lang.Long       => -n1
    case n1: java.math.BigInteger => n1.negate()
    case n1: java.lang.Float      => -n1
    case n1: java.lang.Double     => -n1
    case n1: java.math.BigDecimal => n1.negate()
  }

  private def compare(n1: Number, n2: Number): Int = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Int] {
      override def onBytes(n1: java.lang.Byte, n2: java.lang.Byte): Int                   = n1.compareTo(n2)
      override def onShorts(n1: java.lang.Short, n2: java.lang.Short): Int                = n1.compareTo(n2)
      override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): Int              = n1.compareTo(n2)
      override def onLongs(n1: java.lang.Long, n2: java.lang.Long): Int                   = n1.compareTo(n2)
      override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): Int = n1.compareTo(n2)
      override def onFloats(n1: java.lang.Float, n2: java.lang.Float): Int                = n1.compareTo(n2)
      override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): Int             = n1.compareTo(n2)
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Int = n1.compareTo(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def greater(n1: Number, n2: Number): Boolean        = compare(n1, n2) > 0
  def greaterOrEqual(n1: Number, n2: Number): Boolean = compare(n1, n2) >= 0
  def lesser(n1: Number, n2: Number): Boolean         = compare(n1, n2) < 0
  def lesserOrEqual(n1: Number, n2: Number): Boolean  = compare(n1, n2) <= 0
  def equal(n1: Number, n2: Number): Boolean          = compare(n1, n2) == 0
  def notEqual(n1: Number, n2: Number): Boolean       = compare(n1, n2) != 0

  private def promoteThenSum(@Nullable n1: Number, @Nullable n2: Number)(
      implicit promotionStrategy: ReturningSingleClassPromotionStrategy
  ) = {
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandlerForPromotingMathOp {
        override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer = n1 + n2
        override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long         = n1 + n2
        override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger =
          n1.add(n2)
        override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float     = n1 + n2
        override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double = n1 + n2
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal =
          n1.add(n2)
      })
    }
  }

  protected def withNotNullValues(@Nullable n1: Number, @Nullable n2: Number)(
      f: => Number
  )(implicit promotionStrategy: ReturningSingleClassPromotionStrategy): Number = {
    if (n1 == null) {
      if (n2 == null) null else convertToPromotedType(n2)
    } else if (n2 == null) {
      convertToPromotedType(n1)
    } else {
      f
    }
  }

  protected def withValuesWithTheSameType[R](n1: Number, n2: Number)(
      handler: SameNumericTypeHandler[R]
  )(implicit promotionStrategy: ReturningSingleClassPromotionStrategy): R = {
    val promotedClass = promotionStrategy.promoteClasses(n1.getClass, n2.getClass).klass
    if (promotedClass == classOf[java.lang.Byte]) {
      handler.onBytes(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Byte]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Byte])
      )
    } else if (promotedClass == classOf[java.lang.Short]) {
      handler.onShorts(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Short]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Short])
      )
    } else if (promotedClass == classOf[java.lang.Integer]) {
      handler.onInts(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Integer]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Integer])
      )
    } else if (promotedClass == classOf[java.lang.Long]) {
      handler.onLongs(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Long]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Long])
      )
    } else if (promotedClass == classOf[java.math.BigInteger]) {
      handler.onBigIntegers(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.math.BigInteger]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.math.BigInteger])
      )
    } else if (promotedClass == classOf[java.lang.Float]) {
      handler.onFloats(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Float]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Float])
      )
    } else if (promotedClass == classOf[java.lang.Double]) {
      handler.onDoubles(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Double]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Double])
      )
    } else if (promotedClass == classOf[java.math.BigDecimal]) {
      handler.onBigDecimals(
        SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.math.BigDecimal]),
        SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.math.BigDecimal])
      )
    } else {
      throw new IllegalStateException(
        s"Unexpected result of number promotion: $promotedClass for types: ${n1.getClass} and ${n2.getClass}"
      )
    }
  }

  private def convertToPromotedType(
      n: Number
  )(implicit promotionStrategy: ReturningSingleClassPromotionStrategy): Number = {
    // In some cases type can be promoted to other class e.g. Byte is promoted to Int for sum
    val promotedClass = promotionStrategy.promoteClasses(n.getClass, n.getClass).klass.asInstanceOf[Class[_ <: Number]]
    SpringNumberUtils.convertNumberToTargetClass(n, promotedClass)
  }

  protected trait SameNumericTypeHandler[R] {
    def onBytes(n1: java.lang.Byte, n2: java.lang.Byte): R
    def onShorts(n1: java.lang.Short, n2: java.lang.Short): R
    def onInts(n1: java.lang.Integer, n2: java.lang.Integer): R
    def onLongs(n1: java.lang.Long, n2: java.lang.Long): R
    def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): R
    def onFloats(n1: java.lang.Float, n2: java.lang.Float): R
    def onDoubles(n1: java.lang.Double, n2: java.lang.Double): R
    def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): R
  }

  protected trait SameNumericTypeHandlerReturningNumber extends SameNumericTypeHandler[Number] {
    override def onBytes(n1: lang.Byte, n2: lang.Byte): java.lang.Byte
    override def onShorts(n1: lang.Short, n2: lang.Short): java.lang.Short
    override def onInts(n1: java.lang.Integer, n2: java.lang.Integer): java.lang.Integer
    override def onLongs(n1: java.lang.Long, n2: java.lang.Long): java.lang.Long
    override def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger
    override def onFloats(n1: java.lang.Float, n2: java.lang.Float): java.lang.Float
    override def onDoubles(n1: java.lang.Double, n2: java.lang.Double): java.lang.Double
    override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal
  }

  protected trait SameNumericTypeHandlerForPromotingMathOp extends SameNumericTypeHandlerReturningNumber {
    override final def onBytes(n1: java.lang.Byte, n2: java.lang.Byte): Nothing =
      throw new IllegalStateException("Bytes should be promoted to Ints before operator")
    override final def onShorts(n1: java.lang.Short, n2: java.lang.Short): Nothing =
      throw new IllegalStateException("Bytes should be promoted to Ints before operator")

  }

}

object MathUtils extends MathUtils
