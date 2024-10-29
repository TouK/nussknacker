package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean, Number => JNumber}
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

trait NumericConversion extends Conversion {
  private val numberClass  = classOf[JNumber]
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass)
}

object Conversion {

  private[extension] def toNumber(stringOrNumber: Any): JNumber = stringOrNumber match {
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
        .getOrElse(new JBigDecimal(ss))

    case n: JNumber => n
  }

}

object ToStringConversion extends Conversion {
  override type ResultType = String
  override val resultTypeClass: Class[ResultType]                   = classOf[String]
  override def appliesToConversion(clazz: Class[_]): Boolean        = clazz == classOf[Object]
  override def convertEither(value: Any): Either[Throwable, String] = Right(value.toString)
}

trait CollectionConversion extends Conversion {
  private val unknownClass    = classOf[Object]
  private val collectionClass = classOf[JCollection[_]]

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(collectionClass) || clazz == unknownClass || clazz.isArray)
}
