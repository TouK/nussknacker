package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import scala.util.{Success, Try}
import java.lang.{Boolean => JBoolean, Double => JDouble, Long => JLong, Number => JNumber}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}

class NumericConversionExt(target: Any) {

  def isLong(): JBoolean = toLongEither.isRight

  def toLong(): JLong = toLongEither match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toLongOrNull(): JLong = toLongEither match {
    case Right(value) => value
    case Left(_)      => null
  }

  def isDouble(): JBoolean = toDoubleEither.isRight

  def toDouble(): JDouble = toDoubleEither match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toDoubleOrNull(): JDouble = toDoubleEither match {
    case Right(value) => value
    case Left(_)      => null
  }

  def isBigDecimal(): JBoolean = toBigDecimalEither.isRight

  def toBigDecimal(): JBigDecimal = toBigDecimalEither match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toBigDecimalOrNull(): JBigDecimal = toBigDecimalEither match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def toLongEither: Either[Throwable, JLong] = {
    target match {
      case v: String => Try(JLong.valueOf(toNumber(v).longValue())).toEither
      case v: Number => Right(v.longValue())
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $target to Long"))
    }
  }

  private def toDoubleEither: Either[Throwable, JDouble] = {
    target match {
      case v: String => Try(JDouble.valueOf(toNumber(v).doubleValue())).toEither
      case v: Number => Right(v.doubleValue())
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $target to Double"))
    }
  }

  private def toBigDecimalEither: Either[Throwable, JBigDecimal] = {
    target match {
      case v: String      => Try(new JBigDecimal(v)).toEither
      case v: JBigInteger => Right(new JBigDecimal(v))
      case v: JBigDecimal => Right(v)
      case v: Number      => Try(new JBigDecimal(v.toString)).toEither
      case _              => Left(new IllegalArgumentException(s"Cannot convert: $target to BigDecimal"))
    }
  }

  private def toNumber(stringOrNumber: Any): JNumber = stringOrNumber match {
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

object NumericConversionExt extends ExtensionMethodsHandler {
  private val numberClass  = classOf[JNumber]
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]

  private val definitions = List(
    definition(Typed.typedClass[JBoolean], "isLong", Some("Check whether can be convert to a Long")),
    definition(Typed.typedClass[JLong], "toLong", Some("Convert to Long or throw exception in case of failure")),
    definition(Typed.typedClass[JLong], "toLongOrNull", Some("Convert to Long or null in case of failure")),
    definition(Typed.typedClass[JBoolean], "isDouble", Some("Check whether can be convert to a Double")),
    definition(Typed.typedClass[JDouble], "toDouble", Some("Convert to Double or throw exception in case of failure")),
    definition(Typed.typedClass[JDouble], "toDoubleOrNull", Some("Convert to Double or null in case of failure")),
    definition(Typed.typedClass[JBoolean], "isBigDecimal", Some("Check whether can be convert to a BigDecimal")),
    definition(
      Typed.typedClass[JBigDecimal],
      "toBigDecimal",
      Some("Convert to BigDecimal or throw exception in case of failure")
    ),
    definition(
      Typed.typedClass[JBigDecimal],
      "toBigDecimalOrNull",
      Some("Convert to BigDecimal or null in case of failure")
    ),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = NumericConversionExt
  override val invocationTargetClass: Class[NumericConversionExt] = classOf[NumericConversionExt]

  override def createConverter(
      classLoader: ClassLoader,
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[NumericConversionExt] =
    (target: Any) => new NumericConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass) {
      definitions
    } else {
      Map.empty
    }
  }

  override def applies(clazz: Class[_]): Boolean = true

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = result
    ),
    name = methodName,
    description = desc
  )

}
