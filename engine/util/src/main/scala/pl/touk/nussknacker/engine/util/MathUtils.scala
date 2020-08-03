package pl.touk.nussknacker.engine.util

import java.math.BigInteger

import org.springframework.util.{NumberUtils => SpringNumberUtils}
import pl.touk.nussknacker.engine.api.typed.supertype.{NumberTypesPromotionStrategy, ReturningSingleClassPromotionStrategy}

trait MathUtils {

  def min(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler {
        override def onBytes(n1: Byte, n2: Byte): Byte = Math.min(n1, n2).byteValue()
        override def onShorts(n1: Short, n2: Short): Short = Math.min(n1, n2).shortValue()
        override def onInts(n1: Int, n2: Int): Int = Math.min(n1, n2)
        override def onLongs(n1: Long, n2: Long): Long = Math.min(n1, n2)
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): BigInteger = n1.min(n2)
        override def onFloats(n1: Float, n2: Float): Float = Math.min(n1, n2)
        override def onDoubles(n1: Double, n2: Double): Double = Math.min(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal = n1.min(n2)
      })
    }
  }

  def max(n1: Number, n2: Number): Number = {
    implicit val promotionStrategy: ReturningSingleClassPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler {
        override def onBytes(n1: Byte, n2: Byte): Byte = Math.max(n1, n2).byteValue()
        override def onShorts(n1: Short, n2: Short): Short = Math.max(n1, n2).shortValue()
        override def onInts(n1: Int, n2: Int): Int = Math.max(n1, n2)
        override def onLongs(n1: Long, n2: Long): Long = Math.max(n1, n2)
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): BigInteger = n1.max(n2)
        override def onFloats(n1: Float, n2: Float): Float = Math.max(n1, n2)
        override def onDoubles(n1: Double, n2: Double): Double = Math.max(n1, n2)
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal = n1.max(n2)
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

  private def promoteThenSum(n1: Number, n2: Number)(implicit promotionStrategy: ReturningSingleClassPromotionStrategy) = {
    withNotNullValues(n1, n2) {
      withValuesWithTheSameType(n1, n2)(new SameNumericTypeHandler {
        override def onBytes(n1: Byte, n2: Byte): Byte = throw new IllegalStateException("Bytes should be promoted to Ints before addition")
        override def onShorts(n1: Short, n2: Short): Short = throw new IllegalStateException("Shorts should be promoted to Ints before addition")
        override def onInts(n1: Int, n2: Int): Int = n1 + n2
        override def onLongs(n1: Long, n2: Long): Long = n1 + n2
        override def onBigIntegers(n1: BigInteger, n2: BigInteger): BigInteger = n1.add(n2)
        override def onFloats(n1: Float, n2: Float): Float = n1 + n2
        override def onDoubles(n1: Double, n2: Double): Double = n1 + n2
        override def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal = n1.add(n2)
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

  protected def withValuesWithTheSameType(n1: Number, n2: Number)(handler: SameNumericTypeHandler)
                                         (implicit promotionStrategy: ReturningSingleClassPromotionStrategy): Number = {
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

  protected trait SameNumericTypeHandler {
    def onBytes(n1: Byte, n2: Byte): Byte
    def onShorts(n1: Short, n2: Short): Short
    def onInts(n1: Int, n2: Int): Int
    def onLongs(n1: Long, n2: Long): Long
    def onBigIntegers(n1: java.math.BigInteger, n2: java.math.BigInteger): java.math.BigInteger
    def onFloats(n1: Float, n2: Float): Float
    def onDoubles(n1: Double, n2: Double): Double
    def onBigDecimals(n1: java.math.BigDecimal, n2: java.math.BigDecimal): java.math.BigDecimal
  }

}

object MathUtils extends MathUtils
