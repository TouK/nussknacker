package pl.touk.nussknacker.engine.api.typed.supertype

import cats.data.NonEmptyList
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.util.Try

/**
  * Extending classes are in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
trait NumberTypesPromotionStrategy extends Serializable {

  private val AllNumbers: Seq[Class[_]] =
    StandardTypesClasses.FloatingPointNumbersOrderedFromWidestToNarrowest ++ StandardTypesClasses.DecimalNumbersOrderedFromWidestToNarrowest

  private val cachedPromotionResults: Map[(Class[_], Class[_]), ReturnedType] =
    (for {
      a              <- AllNumbers
      b              <- AllNumbers
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
        } yield promoteClasses(l.runtimeObjType.klass, r.runtimeObjType.klass)
        Typed(allCombinations)
    }
  }

  private def toSingleTypesSet(typ: TypingResult): Either[Unknown.type, NonEmptyList[SingleTypingResult]] =
    typ match {
      case s: SingleTypingResult => Right(NonEmptyList.one(s))
      case u: TypedUnion         => Right(u.possibleTypes)
      case TypedNull             => Left(Unknown)
      case Unknown(_)            => Left(Unknown)
    }

  final def promoteClasses(left: Class[_], right: Class[_]): ReturnedType = {
    val boxedLeft  = ClassUtils.primitiveToWrapper(left)
    val boxedRight = ClassUtils.primitiveToWrapper(right)
    if (!NumberClass.isAssignableFrom(boxedLeft) || !NumberClass.isAssignableFrom(boxedRight))
      throw new IllegalArgumentException(s"One of promoted classes is not a number: $boxedLeft, $boxedRight")
    cachedPromotionResults.getOrElse((boxedLeft, boxedRight), promoteClassesInternal(boxedLeft, boxedRight))
  }

  protected def promoteClassesInternal(left: Class[_], right: Class[_]): ReturnedType

}

trait ReturningSingleClassPromotionStrategy extends NumberTypesPromotionStrategy {

  override type ReturnedType = TypedClass

}

object NumberTypesPromotionStrategy {

  // See org.springframework.expression.spel.ast.OpPlus and so on for details
  object ForMathOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == ByteClass || firstDecimal == ShortClass) {
        Typed.typedClass(IntegerClass)
      } else {
        Typed.typedClass(firstDecimal)
      }
    }

  }

  // In some cases will be better to always promote types to wider types like Float -> Double or Integer -> Long.
  // Especially when you can't estimate number of operations that will be performed
  object ForLargeNumbersOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleFloatingType(firstFloating: Class[_]): TypedClass = {
      if (firstFloating == FloatClass) {
        Typed.typedClass(DoubleClass)
      } else {
        Typed.typedClass(firstFloating)
      }
    }

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == ByteClass || firstDecimal == ShortClass || firstDecimal == IntegerClass) {
        Typed.typedClass(LongClass)
      } else {
        Typed.typedClass(firstDecimal)
      }
    }

  }

  object ForLargeFloatingNumbersOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleFloatingType(firstFloating: Class[_]): TypedClass = {
      if (firstFloating == BigDecimalClass) {
        Typed.typedClass(BigDecimalClass)
      } else {
        Typed.typedClass(DoubleClass)
      }
    }

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == BigIntegerClass) {
        Typed.typedClass(BigDecimalClass)
      } else {
        Typed.typedClass(DoubleClass)
      }
    }

  }

  object ForMinMax extends BaseToCommonWidestTypePromotionStrategy

  abstract class BaseToCommonWidestTypePromotionStrategy extends ReturningSingleClassPromotionStrategy {

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypedClass = {
      val both = List(left, right)
      if (both.forall(StandardTypesClasses.isFloatingPointNumber)) {
        val firstFloating =
          both.map(n => FloatingPointNumbersOrderedFromWidestToNarrowest.indexOf(n) -> n).sortBy(_._1).map(_._2).head
        handleFloatingType(firstFloating)
      } else if (both.forall(StandardTypesClasses.isDecimalNumber)) {
        val firstDecimal =
          both
            .map(n => StandardTypesClasses.DecimalNumbersOrderedFromWidestToNarrowest.indexOf(n) -> n)
            .sortBy(_._1)
            .map(_._2)
            .head
        handleDecimalType(firstDecimal)
      } else if (both
          .exists(StandardTypesClasses.isDecimalNumber) && both.exists(StandardTypesClasses.isFloatingPointNumber)) {
        val floating = both.find(StandardTypesClasses.isFloatingPointNumber).get
        handleFloatingType(floating)
      } else { // unknown Number
        Typed.typedClass[Number]
      }
    }

    protected def handleFloatingType(firstFloating: Class[_]): TypedClass = Typed.typedClass(firstFloating)

    protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = Typed.typedClass(firstDecimal)

  }

  // See org.springframework.expression.spel.ast.OperatorPower for details
  object ForPowerOperation extends NumberTypesPromotionStrategy {

    override type ReturnedType = TypingResult

    override def promoteClassesInternal(left: Class[_], right: Class[_]): TypingResult = {
      if (left == BigDecimalClass) {
        Typed[java.math.BigDecimal]
      } else if (left == BigIntegerClass) {
        Typed[java.math.BigInteger]
      } else if (left == DoubleClass || right == DoubleClass ||
        left == FloatClass || right == FloatClass) {
        Typed[Double]
      } else if (left == LongClass || right == LongClass) {
        Typed[Long]
      } else {
        // This is the only place where we return union. The runtime type depends on whether there was overflow or not.
        // We should consider using just the Number here
        Typed(Typed[Integer], Typed[Long])
      }
    }

  }

}
