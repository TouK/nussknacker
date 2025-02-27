package pl.touk.nussknacker.engine.api.typed.supertype

import cats.data.NonEmptyList
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.AllNumbers
import pl.touk.nussknacker.engine.api.typed.typing._

import java.lang
import scala.util.Try

/**
  * Extending classes are in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
trait NumberTypesPromotionStrategy extends Serializable {

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
      case Unknown               => Left(Unknown)
    }

  final def promoteClasses(left: Class[_], right: Class[_]): ReturnedType = {
    val boxedLeft  = ClassUtils.primitiveToWrapper(left)
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

  // In some cases will be better to always promote types to wider types like Float -> Double or Integer -> Long.
  // Especially when you can't estimate number of operations that will be performed
  object ForLargeNumbersOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleFloatingType(firstFloating: Class[_]): TypedClass = {
      if (firstFloating == classOf[lang.Float]) {
        Typed.typedClass(classOf[Double])
      } else {
        Typed.typedClass(firstFloating)
      }
    }

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == classOf[lang.Byte] || firstDecimal == classOf[lang.Short] || firstDecimal == classOf[
          lang.Integer
        ]) {
        Typed.typedClass(classOf[Long])
      } else {
        Typed.typedClass(firstDecimal)
      }
    }

  }

  object ForLargeFloatingNumbersOperation extends BaseToCommonWidestTypePromotionStrategy {

    override protected def handleFloatingType(firstFloating: Class[_]): TypedClass = {
      if (firstFloating == classOf[java.math.BigDecimal]) {
        Typed.typedClass(classOf[java.math.BigDecimal])
      } else {
        Typed.typedClass(classOf[Double])
      }
    }

    override protected def handleDecimalType(firstDecimal: Class[_]): TypedClass = {
      if (firstDecimal == classOf[java.math.BigInteger]) {
        Typed.typedClass(classOf[java.math.BigDecimal])
      } else {
        Typed.typedClass(classOf[Double])
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
      if (left == classOf[java.math.BigDecimal]) {
        Typed[java.math.BigDecimal]
      } else if (left == classOf[java.math.BigInteger]) {
        Typed[java.math.BigInteger]
      } else if (left == classOf[java.lang.Double] || right == classOf[java.lang.Double] ||
        left == classOf[java.lang.Float] || right == classOf[java.lang.Float]) {
        Typed[Double]
      } else if (left == classOf[java.lang.Long] || right == classOf[java.lang.Long]) {
        Typed[Long]
      } else {
        // This is the only place where we return union. The runtime type depends on whether there was overflow or not.
        // We should consider using just the Number here
        Typed(Typed[java.lang.Integer], Typed[Long])
      }
    }

  }

}
