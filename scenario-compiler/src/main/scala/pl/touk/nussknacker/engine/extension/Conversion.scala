package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions
import pl.touk.nussknacker.springframework.util.NumberUtilsConsts

import java.lang.{
  Boolean => JBoolean,
  Byte => JByte,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong,
  Number => JNumber,
  Short => JShort
}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.util.{Collection => JCollection}
import scala.reflect.{ClassTag, classTag}
import scala.util.{Success, Try}

abstract class Conversion[T >: Null <: AnyRef: ClassTag] {
  private[extension] val numberClass  = classOf[JNumber]
  private[extension] val stringClass  = classOf[String]
  private[extension] val unknownClass = classOf[Object]

  val resultTypeClass: Class[T]  = classTag[T].runtimeClass.asInstanceOf[Class[T]]
  val typingResult: TypingResult = Typed.typedClass(resultTypeClass)
  val typingFunction: TypingResult => ValidatedNel[GenericFunctionTypingError, TypingResult] =
    _ => typingResult.validNel

  def convertEither(value: Any): Either[Throwable, T]
  def appliesToConversion(clazz: Class[_]): Boolean
  def canConvert(value: Any): JBoolean = convertEither(value).isRight
}

abstract class ToNumericConversion[T >: Null <: AnyRef: ClassTag] extends Conversion[T] {
  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass)

  private[extension] def toNumberEither(stringOrNumber: Any): Either[Throwable, JNumber] = stringOrNumber match {
    case s: CharSequence =>
      val ss = s.toString
      // we pick the narrowest type as possible to reduce the amount of memory and computations overheads
      val tries: List[Try[JNumber]] = List(
        Try(java.lang.Integer.parseInt(ss)),
        Try(java.lang.Long.parseLong(ss)),
        Try(java.lang.Double.parseDouble(ss)),
        Try(new JBigDecimal(ss)),
        Try(new JBigInteger(ss))
      )

      tries
        .collectFirst { case Success(value) =>
          value
        }
        .toRight(new IllegalArgumentException(s"Cannot convert: '$stringOrNumber' to Number"))

    case n: JNumber => Right(n)
  }

}

object ToBigDecimalConversion extends ToNumericConversion[JBigDecimal] {

  override def convertEither(value: Any): Either[Throwable, JBigDecimal] =
    value match {
      case v: JBigDecimal => Right(v)
      case v: JBigInteger => Right(new JBigDecimal(v).setScale(NumberUtilsConsts.DEFAULT_BIG_DECIMAL_SCALE))
      case v: Number      => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String =>
        Try({
          val a = new JBigDecimal(v)
          a.setScale(Math.max(a.scale(), NumberUtilsConsts.DEFAULT_BIG_DECIMAL_SCALE))
        }).toEither
      case _ => Left(new IllegalArgumentException(s"Cannot convert: $value to BigDecimal"))
    }

}

object ToBooleanConversion extends Conversion[JBoolean] {
  private val cannotConvertException = (value: Any) =>
    new IllegalArgumentException(s"Cannot convert: $value to Boolean")
  private val allowedClassesForConversion: Set[Class[_]] = Set(stringClass, unknownClass)

  override def appliesToConversion(clazz: Class[_]): Boolean = allowedClassesForConversion.contains(clazz)

  override def convertEither(value: Any): Either[Throwable, JBoolean] = value match {
    case b: JBoolean => Right(b)
    case s: String   => stringToBoolean(s).toRight(cannotConvertException(value))
    case _           => Left(cannotConvertException(value))
  }

  private def stringToBoolean(value: String): Option[JBoolean] =
    if ("true".equalsIgnoreCase(value)) {
      Some(true)
    } else if ("false".equalsIgnoreCase(value)) {
      Some(false)
    } else {
      None
    }

}

object ToDoubleConversion extends ToNumericConversion[JDouble] {

  override def convertEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(NumberUtils.convertNumberToTargetClass(v, resultTypeClass))
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

}

object ToLongConversion extends ToNumericConversion[JLong] {

  override def convertEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

}

object ToStringConversion extends Conversion[String] {
  override def appliesToConversion(clazz: Class[_]): Boolean        = clazz == unknownClass
  override def convertEither(value: Any): Either[Throwable, String] = Right(value.toString)
}

object ToByteConversion extends ToNumericConversion[JByte] {

  override def convertEither(value: Any): Either[Throwable, JByte] = value match {
    case v: JByte   => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Byte"))
  }

}

object ToShortConversion extends ToNumericConversion[JShort] {

  override def convertEither(value: Any): Either[Throwable, JShort] = value match {
    case v: JShort  => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Short"))
  }

}

object ToIntegerConversion extends ToNumericConversion[JInteger] {

  override def convertEither(value: Any): Either[Throwable, JInteger] = value match {
    case v: JInteger => Right(v)
    case v: JNumber  => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String   => toNumberEither(v).flatMap(convertEither)
    case _           => Left(new IllegalArgumentException(s"Cannot convert: $value to Integer"))
  }

}

object ToFloatConversion extends ToNumericConversion[JFloat] {

  override def convertEither(value: Any): Either[Throwable, JFloat] = value match {
    case v: JFloat  => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Float"))
  }

}

object ToBigIntegerConversion extends ToNumericConversion[JBigInteger] {

  override def convertEither(value: Any): Either[Throwable, JBigInteger] = value match {
    case v: JBigInteger => Right(v)
    case v: JBigDecimal => Right(v.toBigInteger)
    case v: JNumber     => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String      => toNumberEither(v).flatMap(convertEither)
    case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigInteger"))
  }

}

final class FromStringConversion[T >: Null <: AnyRef: ClassTag](convert: String => T) extends Conversion[T] {
  override def convertEither(value: Any): Either[Throwable, T] = Try(convert(value.asInstanceOf[String])).toEither
  override def appliesToConversion(clazz: Class[_]): Boolean   = clazz == stringClass || clazz == unknownClass
}
