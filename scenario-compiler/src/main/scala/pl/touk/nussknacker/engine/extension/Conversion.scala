package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{
  Boolean => JBoolean,
  Byte => JByte,
  Float => JFloat,
  Integer => JInteger,
  Number => JNumber,
  Short => JShort
}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.util.{Collection => JCollection}
import scala.util.{Success, Try}

trait Conversion {
  type ResultType >: Null <: AnyRef
  val resultTypeClass: Class[ResultType]

  def convertEither(value: Any): Either[Throwable, ResultType]
  def appliesToConversion(clazz: Class[_]): Boolean

  def canConvert(value: Any): JBoolean = convertEither(value).isRight

  def convert(value: Any): ResultType = convertEither(value) match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def convertOrNull(value: Any): ResultType = convertEither(value) match {
    case Right(value) => value
    case Left(_)      => null
  }

  def typingResult: TypingResult = Typed.typedClass(resultTypeClass)
  def typingFunction(invocationTarget: TypingResult): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    typingResult.validNel
}

object Conversion {

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

trait ConversionExt extends ExtensionMethodsHandler with Conversion {
  val definitions: List[MethodDefinition]

  // Conversion extension should be available for every class in a runtime
  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (appliesToConversion(clazz)) {
      definitions.groupBy(_.name)
    } else {
      Map.empty
    }
  }

  private[extension] def definition(result: TypingResult, methodName: String, desc: Option[String]) =
    StaticMethodDefinition(
      signature = MethodTypeInfo.noArgTypeInfo(result),
      name = methodName,
      description = desc
    )

}

object ToStringConversion extends Conversion {
  override type ResultType = String
  override val resultTypeClass: Class[ResultType]                   = classOf[String]
  override def appliesToConversion(clazz: Class[_]): Boolean        = clazz == classOf[Object]
  override def convertEither(value: Any): Either[Throwable, String] = Right(value.toString)
}

trait ToCollectionConversion extends Conversion {
  private val unknownClass    = classOf[Object]
  private val collectionClass = classOf[JCollection[_]]

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(collectionClass) || clazz == unknownClass || clazz.isArray)
}

trait ToNumericConversion extends Conversion {
  private val numberClass  = classOf[JNumber]
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass)
}

object ToByteConversion extends ToNumericConversion {
  override type ResultType = JByte
  override val resultTypeClass: Class[JByte] = classOf[JByte]

  override def convertEither(value: Any): Either[Throwable, JByte] = value match {
    case v: JByte   => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => Conversion.toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Byte"))
  }

}

object ToShortConversion extends ToNumericConversion {
  override type ResultType = JShort
  override val resultTypeClass: Class[JShort] = classOf[JShort]

  override def convertEither(value: Any): Either[Throwable, JShort] = value match {
    case v: JShort  => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => Conversion.toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Short"))
  }

}

object ToIntegerConversion extends ToNumericConversion {
  override type ResultType = JInteger
  override val resultTypeClass: Class[JInteger] = classOf[JInteger]

  override def convertEither(value: Any): Either[Throwable, JInteger] = value match {
    case v: JInteger => Right(v)
    case v: JNumber  => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String   => Conversion.toNumberEither(v).flatMap(convertEither)
    case _           => Left(new IllegalArgumentException(s"Cannot convert: $value to Integer"))
  }

}

object ToFloatConversion extends ToNumericConversion {
  override type ResultType = JFloat
  override val resultTypeClass: Class[JFloat] = classOf[JFloat]

  override def convertEither(value: Any): Either[Throwable, JFloat] = value match {
    case v: JFloat  => Right(v)
    case v: JNumber => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String  => Conversion.toNumberEither(v).flatMap(convertEither)
    case _          => Left(new IllegalArgumentException(s"Cannot convert: $value to Float"))
  }

}

object ToBigIntegerConversion extends ToNumericConversion {
  override type ResultType = JBigInteger
  override val resultTypeClass: Class[JBigInteger] = classOf[JBigInteger]

  override def convertEither(value: Any): Either[Throwable, JBigInteger] = value match {
    case v: JBigInteger => Right(v)
    case v: JBigDecimal => Right(v.toBigInteger)
    case v: JNumber     => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
    case v: String      => Conversion.toNumberEither(v).flatMap(convertEither)
    case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigInteger"))
  }

}
