package pl.touk.nussknacker.engine.api.typed.supertype

import java.lang

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.AllNumbers
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.util.Try

trait NumberTypesPromotionStrategy extends Serializable {

  private val cachedPromotionResults: Map[(Class[_], Class[_]), ReturnedType] =
    (for {
      a <- AllNumbers
      b <- AllNumbers
      existingResult <- Try(promoteClassesInternal(a, b)).toOption
    } yield (a, b) -> existingResult).toMap

  type ReturnedType <: TypingResult

  def promoteSingle(typ: TypingResult): TypingResult = promote(typ, typ)

  def promote(left: TypingResult, right: TypingResult): TypingResult = {
    (toSingleTypesSet(left), toSingleTypesSet(right)) match {
      case (Left(Unknown), _) => Unknown
      case (_, Left(Unknown)) => Unknown
      case (Right(lSet), Right(rSet)) =>
        val allCombinations = for {
          l <- lSet
          r <- rSet
        } yield promoteClasses(l.objType.klass, r.objType.klass)
        Typed(allCombinations)
    }
  }

  private def toSingleTypesSet(typ: TypingResult) : Either[Unknown.type, Set[SingleTypingResult]] =
    typ match {
      case s: SingleTypingResult => Right(Set(s))
      case u: TypedUnion => Right(u.possibleTypes)
      case Unknown => Left(Unknown)
    }

  final def promoteClasses(left: Class[_], right: Class[_]): ReturnedType = {
    val boxedLeft = ClassUtils.primitiveToWrapper(left)
    val boxedRight = ClassUtils.primitiveToWrapper(right)
    if (!classOf[Number].isAssignableFrom(boxedLeft) || !classOf[Number].isAssignableFrom(boxedRight))
      throw new IllegalArgumentException(s"One of promoted classes is not a number: $boxedLeft, $boxedRight")
    cachedPromotionResults.getOrElse((boxedLeft, boxedRight), promoteClassesInternal(boxedLeft, boxedRight))
  }

  protected def promoteClassesInternal(left: Class[_], right: Class[_]): ReturnedType

}

trait ReturningSingleClassPromotionStrategy extends NumberTypesPromotionStrategy {

  override type ReturnedType = TypedClass

}

object NumberTypesPromotionStrategy {

  // The order is important for determining promoted type (it is from widest to narrowest type)
  private val FloatingNumbers: Seq[Class[_]] = IndexedSeq(
    classOf[java.math.BigDecimal],
    classOf[java.lang.Double],
    classOf[java.lang.Float]
  )

  def isFloatingNumber(clazz: Class[_]): Boolean = FloatingNumbers.contains(clazz)

  // The order is important for determining promoted type (it is from widest to narrowest type)
  val DecimalNumbers: Seq[Class[_]] = IndexedSeq(
    classOf[java.math.BigInteger],
    classOf[java.lang.Long],
    classOf[java.lang.Integer],
    classOf[java.lang.Short],
    classOf[java.lang.Byte]
  )

  val AllNumbers: Seq[Class[_]] = FloatingNumbers ++ DecimalNumbers

  def isDecimalNumber(clazz: Class[_]): Boolean = DecimalNumbers.contains(clazz)

  // See org.springframework.expression.spel.ast.OpPlus and so on for details
  object ForMathOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == classOf[lang.Byte] || firstDecimal == classOf[lang.Short]) {
        Typed.typedClass(classOf[lang.Integer])
      } else {
        Typed.typedClass(firstDecimal)
      }
    }

  }

  // In some cases will be better to alswys promote types to wider types like Float -> Double or Integer -> Long.
  // Especially when you can't estimate number of operations that will be performed
  object ForLargeNumbersOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleFloatingType(firstFloating: Class[_]): TypedClass = {
      if (firstFloating == classOf[lang.Float]) {
        Typed.typedClass(classOf[lang.Double])
      } else {
        Typed.typedClass(firstFloating)
      }
    }

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == classOf[lang.Byte] || firstDecimal == classOf[lang.Short] || firstDecimal == classOf[lang.Integer]) {
        Typed.typedClass(classOf[lang.Long])
      } else {
        Typed.typedClass(firstDecimal)
      }
    }

  }

  object ForMinMax extends BaseToCommonWidestTypePromotionStrategy

  abstract class BaseToCommonWidestTypePromotionStrategy extends ReturningSingleClassPromotionStrategy {

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypedClass = {
      val both = List(left, right)
      if (both.forall(FloatingNumbers.contains)) {
        val firstFloating = both.map(n => FloatingNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head
        handleFloatingType(firstFloating)
      } else if (both.forall(DecimalNumbers.contains)) {
        val firstDecimal = both.map(n => DecimalNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head
        handleDecimalType(firstDecimal)
      } else if (both.exists(DecimalNumbers.contains) && both.exists(FloatingNumbers.contains)) {
        val floating = both.find(FloatingNumbers.contains).get
        handleFloatingType(floating)
      } else { // unknown Number
        Typed.typedClass[java.lang.Double]
      }
    }

    protected def handleFloatingType(firstFloating: Class[_]): TypedClass = Typed.typedClass(firstFloating)

    protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = Typed.typedClass(firstDecimal)

  }

  object ToSupertype extends ReturningSingleClassPromotionStrategy {

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypedClass = {
      if (left.isAssignableFrom(right)) {
        Typed.typedClass(left)
      } else if (right.isAssignableFrom(left)) {
        Typed.typedClass(right)
      } else {
        Typed.typedClass[Number]
      }
    }

  }

  // See org.springframework.expression.spel.ast.OperatorPower for details
  object ForPowerOperation extends NumberTypesPromotionStrategy {

    override type ReturnedType = TypingResult

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypingResult = {
      if (left == classOf[java.math.BigDecimal]) {
        Typed[java.math.BigDecimal]
      } else if (left == classOf[java.math.BigInteger]) {
        Typed[java.math.BigInteger]
      } else if (left == classOf[java.lang.Double] || right == classOf[java.lang.Double] ||
        left == classOf[java.lang.Float] || right == classOf[java.lang.Float]) {
        Typed[java.lang.Double]
      } else if (left == classOf[java.lang.Long] || right == classOf[java.lang.Long]) {
        Typed[java.lang.Long]
      } else {
        Typed(Typed[java.lang.Integer], Typed[java.lang.Long])  // it depends if there was overflow or not
      }
    }

  }

}
