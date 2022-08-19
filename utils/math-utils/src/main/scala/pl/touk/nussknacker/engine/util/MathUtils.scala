package pl.touk.nussknacker.engine.util

import org.springframework.util.{NumberUtils => SpringNumberUtils}
import pl.touk.nussknacker.engine.api.typed.supertype.{NumberTypesPromotionStrategy, ReturningSingleClassPromotionStrategy}

import java.math.BigInteger

trait MathUtils {

  def min(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
        override def onBytes(n1: Byte, n2: Byte): Number = Math.min(n1, n2).byteValue()
        override def onShorts(n1: Short, n2: Short): Number = Math.min(n1, n2).shortValue()
        override def onInts(n1: Int, n2: Int): Number = Math.min(n1, n2)
        override def onLongs(n1: Long, n2: Long): Number = Math.min(n1, n2)
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.min(n2)
        override def onFloats(n1: Float, n2: Float): Number = Math.min(n1, n2)
        override def onDoubles(n1: Double, n2: Double): Number = Math.min(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.min(n2)
      })
    }
  }

  def max(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
        override def onBytes(n1: Byte, n2: Byte): Number = Math.max(n1, n2).byteValue()
        override def onShorts(n1: Short, n2: Short): Number = Math.max(n1, n2).shortValue()
        override def onInts(n1: Int, n2: Int): Number = Math.max(n1, n2)
        override def onLongs(n1: Long, n2: Long): Number = Math.max(n1, n2)
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.max(n2)
        override def onFloats(n1: Float, n2: Float): Number = Math.max(n1, n2)
        override def onDoubles(n1: Double, n2: Double): Number = Math.max(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.max(n2)
      })
    }
  }

  def sum(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMathOperation
    promoteThenSum(n1, n2)
  }

  def largeSum(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForLargeNumbersOperation
    promoteThenSum(n1, n2)
  }

  def plus(n1: Number, n2: Number): Number = sum(n1, n2)

  def minus(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
      override def onBytes(n1: Byte, n2: Byte): Number = n1 - n2
      override def onShorts(n1: Short, n2: Short): Number = n1 - n2
      override def onInts(n1: Int, n2: Int): Number = n1 - n2
      override def onLongs(n1: Long, n2: Long): Number = n1 - n2
      override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.subtract(n2)
      override def onFloats(n1: Float, n2: Float): Number = n1 - n2
      override def onDoubles(n1: Double, n2: Double): Number = n1 - n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.subtract(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def multiply(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
      override def onBytes(n1: Byte, n2: Byte): Number = n1 * n2
      override def onShorts(n1: Short, n2: Short): Number = n1 * n2
      override def onInts(n1: Int, n2: Int): Number = n1 * n2
      override def onLongs(n1: Long, n2: Long): Number = n1 * n2
      override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.multiply(n2)
      override def onFloats(n1: Float, n2: Float): Number = n1 * n2
      override def onDoubles(n1: Double, n2: Double): Number = n1 * n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.multiply(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def divide(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
      override def onBytes(n1: Byte, n2: Byte): Number = n1 / n2
      override def onShorts(n1: Short, n2: Short): Number = n1 / n2
      override def onInts(n1: Int, n2: Int): Number = n1 / n2
      override def onLongs(n1: Long, n2: Long): Number = n1 / n2
      override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.divide(n2)
      override def onFloats(n1: Float, n2: Float): Number = n1 / n2
      override def onDoubles(n1: Double, n2: Double): Number = n1 / n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.divide(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def remainder(n1: Number, n2: Number): Number = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
      override def onBytes(n1: Byte, n2: Byte): Number = n1 % n2
      override def onShorts(n1: Short, n2: Short): Number = n1 % n2
      override def onInts(n1: Int, n2: Int): Number = n1 % n2
      override def onLongs(n1: Long, n2: Long): Number = n1 % n2
      override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.remainder(n2)
      override def onFloats(n1: Float, n2: Float): Number = n1 % n2
      override def onDoubles(n1: Double, n2: Double): Number = n1 % n2
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.remainder(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def negate(n1: Number): Number = n1 match {
    case n1: java.lang.Byte => -n1
    case n1: java.lang.Short => -n1
    case n1: java.lang.Integer => -n1
    case n1: java.lang.Long => -n1
    case n1: java.math.BigInteger => n1.negate()
    case n1: java.lang.Float => -n1
    case n1: java.lang.Double => -n1
    case n1: java.math.BigDecimal => n1.negate()
  }

  private def compare(n1: Number, n2: Number): Int = {
    withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Int] {
      override def onBytes(n1: Byte, n2: Byte): Int = n1.compareTo(n2)
      override def onShorts(n1: Short, n2: Short): Int = n1.compareTo(n2)
      override def onInts(n1: Int, n2: Int): Int = n1.compareTo(n2)
      override def onLongs(n1: Long, n2: Long): Int = n1.compareTo(n2)
      override def onBigIntegers(n1: BigInteger, n2: BigInteger): Int = n1.compareTo(n2)
      override def onFloats(n1: Float, n2: Float): Int = n1.compareTo(n2)
      override def onDoubles(n1: Double, n2: Double): Int = n1.compareTo(n2)
      override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Int = n1.compareTo(n2)
    })(NumberTypesPromotionStrategy.ForMathOperation)
  }

  def greater(n1: Number, n2: Number): Boolean = compare(n1, n2) > 0
  def greaterOrEqual(n1: Number, n2: Number): Boolean = compare(n1, n2) >= 0
  def lesser(n1: Number, n2: Number): Boolean = compare(n1, n2) < 0
  def lesserOrEqual(n1: Number, n2: Number): Boolean = compare(n1, n2) <= 0
  def equal(n1: Number, n2: Number): Boolean = compare(n1, n2) == 0
  def notEqual(n1: Number, n2: Number): Boolean = compare(n1, n2) != 0

  private def promoteThenSum(n1: Number, n2: Number)(implicit promotionStrategy: ReturningSingleClassPromotionStrategy) = {
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler[Number] {
        override def onBytes(n1: Byte, n2: Byte): Number = n1 + n2
        override def onShorts(n1: Short, n2: Short): Number = n1 + n2
        override def onInts(n1: Int, n2: Int): Number = n1 + n2
        override def onLongs(n1: Long, n2: Long): Number = n1 + n2
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): Number = n1.add(n2)
        override def onFloats(n1: Float, n2: Float): Number = n1 + n2
        override def onDoubles(n1: Double, n2: Double): Number = n1 + n2
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): Number = n1.add(n2)
      })
    }
  }

  protected def withNotNullValues(n1: Number, n2: Number)(f: => Number)
                                 (implicit promotionStrategy: ReturningSingleClassPromotionStrategy): Number = {
    if (n1 == null) {
      if (n2 == null) null else convertToPromotedType(n2)
    } else if (n2 == null) {
      convertToPromotedType(n1)
    } else {
      f
    }
  }

  protected def withValuesWithTheSameType[R](n1: Number, n2: Number)(handler: SameNumericTypeHandler[R])
                                            (implicit promotionStrategy: ReturningSingleClassPromotionStrategy): R = {
    val promotedClass = promotionStrategy.promoteClasses(n1.getClass, n2.getClass).klass
    if (promotedClass == classOf[java.lang.Byte]) {
      handler.onBytes(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Byte]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Byte]))
    } else if (promotedClass == classOf[java.lang.Short]) {
      handler.onShorts(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Short]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Short]))
    } else if (promotedClass == classOf[java.lang.Integer]) {
      handler.onInts(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Integer]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Integer]))
    } else if (promotedClass == classOf[java.lang.Long]) {
      handler.onLongs(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Long]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Long]))
    } else if (promotedClass == classOf[java.math.BigInteger]) {
      handler.onBigIntegers(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.math.BigInteger]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.math.BigInteger]))
    } else if (promotedClass == classOf[java.lang.Float]) {
      handler.onFloats(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Float]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Float]))
    } else if (promotedClass == classOf[java.lang.Double]) {
      handler.onDoubles(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.lang.Double]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.lang.Double]))
    } else if (promotedClass == classOf[java.math.BigDecimal]) {
      handler.onBigDecimals(SpringNumberUtils.convertNumberToTargetClass(n1, classOf[java.math.BigDecimal]), SpringNumberUtils.convertNumberToTargetClass(n2, classOf[java.math.BigDecimal]))
    } else {
      throw new IllegalStateException(s"Unexpected result of number promotion: $promotedClass for types: ${n1.getClass} and ${n2.getClass}")
    }
  }

  private def convertToPromotedType(n: Number)(implicit promotionStrategy: ReturningSingleClassPromotionStrategy): Number = {
    // In some cases type can be promoted to other class e.g. Byte is promoted to Int for sum
    val promotedClass = promotionStrategy.promoteClasses(n.getClass, n.getClass).klass.asInstanceOf[Class[_ <: Number]]
    SpringNumberUtils.convertNumberToTargetClass(n, promotedClass)
  }

  protected trait SameNumericTypeHandler[R] {
    def onBytes(n1: Byte, n2: Byte): R
    def onShorts(n1: Short, n2: Short): R
    def onInts(n1: Int, n2: Int): R
    def onLongs(n1: Long, n2: Long): R
    def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): R
    def onFloats(n1: Float, n2: Float): R
    def onDoubles(n1: Double, n2: Double): R
    def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): R
  }
}

object MathUtils extends MathUtils
