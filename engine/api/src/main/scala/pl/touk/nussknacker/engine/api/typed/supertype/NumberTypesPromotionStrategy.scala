package pl.touk.nussknacker.engine.api.typed.supertype

import java.lang

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing._

trait NumberTypesPromotionStrategy extends Serializable {

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

  final def promoteClasses(left: Class[_], right: Class[_]): TypingResult = {
    val boxedLeft = ClassUtils.primitiveToWrapper(left)
    val boxedRight = ClassUtils.primitiveToWrapper(right)
    if (!classOf[Number].isAssignableFrom(boxedLeft) || !classOf[Number].isAssignableFrom(boxedRight))
      throw new IllegalArgumentException(s"One of promoted classes is not a number: $boxedLeft, $boxedRight")
    promoteClassesInternal(boxedLeft, boxedRight)
  }

  protected def promoteClassesInternal(left: Class[_], right: Class[_]): TypingResult

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

  def isDecimalNumber(clazz: Class[_]): Boolean = DecimalNumbers.contains(clazz)

  // See org.springframework.expression.spel.ast.OpPlus and so on for details
  object ForMathOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleDecimalType(firstDecimal: Class[_]): TypingResult = {
      if (firstDecimal == classOf[lang.Byte] || firstDecimal == classOf[lang.Short]) {
        Typed(classOf[Integer])
      } else {
        Typed(firstDecimal)
      }
    }

  }

  object ForMinMax extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleDecimalType(firstDecimal: Class[_]): TypingResult = {
      Typed(firstDecimal)
    }

  }

  abstract class BaseToCommonWidestTypePromotionStrategy extends NumberTypesPromotionStrategy {

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypingResult = {
      val both = List(left, right)
      if (both.forall(FloatingNumbers.contains)) {
        Typed(both.map(n => FloatingNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head)
      } else if (both.forall(DecimalNumbers.contains)) {
        val firstDecimal = both.map(n => DecimalNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head
        handleDecimalType(firstDecimal)
      } else if (both.exists(DecimalNumbers.contains) && both.exists(FloatingNumbers.contains)) {
        Typed(both.find(FloatingNumbers.contains).get)
      } else { // unknown Number
        Typed[java.lang.Double]
      }
    }

    protected def handleDecimalType(firstDecimal: Class[_]): TypingResult

  }

  object ToSupertype extends NumberTypesPromotionStrategy {

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypingResult = {
      if (left.isAssignableFrom(right)) {
        Typed(left)
      } else if (right.isAssignableFrom(left)) {
        Typed(right)
      } else {
        Typed[Number]
      }
    }

  }

  // See org.springframework.expression.spel.ast.OperatorPower for details
  object ForPowerOperation extends NumberTypesPromotionStrategy {

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
