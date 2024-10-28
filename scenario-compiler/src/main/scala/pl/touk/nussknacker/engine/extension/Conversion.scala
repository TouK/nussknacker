package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.extension.Conversion.toNumber
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import scala.util.{Success, Try}
import java.util.{
  ArrayList => JArrayList,
  Collection => JCollection,
  HashMap => JHashMap,
  List => JList,
  Map => JMap,
  Set => JSet
}
import java.lang.{Boolean => JBoolean, Double => JDouble, Long => JLong, Number => JNumber}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}

sealed trait Conversion {
  type ResultType
  val resultTypeClass: Class[ResultType]

  def canConvert(value: Any): JBoolean
  def convert(value: Any): ResultType
  def convertOrNull(value: Any): ResultType

  def typingResult: TypingResult        = Typed.typedClass(resultTypeClass)
  def simpleSupportedResultTypeName     = ReflectUtils.simpleNameWithoutSuffix(resultTypeClass)
  def supportedResultTypes: Set[String] = Set(resultTypeClass.getName, simpleSupportedResultTypeName)
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

object ToLongConversion extends Conversion {
  override type ResultType = JLong
  override val resultTypeClass: Class[JLong] = classOf[JLong]

  override def canConvert(value: Any): JBoolean = toLongEither(value).isRight

  override def convert(value: Any): JLong = toLongEither(value) match {
    case Right(result) => result
    case Left(ex)      => throw ex
  }

  override def convertOrNull(value: Any): JLong = toLongEither(value) match {
    case Right(result) => result
    case Left(_)       => null
  }

  private def toLongEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Right(v.longValue())
      case v: String => Try(JLong.valueOf(toNumber(v).longValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

}

object ToDoubleConversion extends Conversion {
  override type ResultType = JDouble
  override val resultTypeClass: Class[JDouble] = classOf[JDouble]

  override def canConvert(value: Any): JBoolean = toDoubleEither(value).isRight

  override def convert(value: Any): JDouble = toDoubleEither(value) match {
    case Right(result) => result
    case Left(ex)      => throw ex
  }

  override def convertOrNull(value: Any): JDouble = toDoubleEither(value) match {
    case Right(result) => result
    case Left(_)       => null
  }

  private def toDoubleEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(v.doubleValue())
      case v: String => Try(JDouble.valueOf(toNumber(v).doubleValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

}

object ToBigDecimalConversion extends Conversion {
  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override def canConvert(value: Any): JBoolean = toBigDecimalEither(value).isRight

  override def convert(value: Any): JBigDecimal = toBigDecimalEither(value) match {
    case Right(result) => result
    case Left(ex)      => throw ex
  }

  override def convertOrNull(value: Any): JBigDecimal = toBigDecimalEither(value) match {
    case Right(result) => result
    case Left(_)       => null
  }

  private def toBigDecimalEither(value: Any): Either[Throwable, JBigDecimal] =
    value match {
      case v: JBigDecimal => Right(v)
      case v: JBigInteger => Right(new JBigDecimal(v))
      case v: String      => Try(new JBigDecimal(v)).toEither
      case v: Number      => Try(new JBigDecimal(v.toString)).toEither
      case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigDecimal"))
    }

}

object ToBooleanConversion extends Conversion {
  private val cannotConvertException = (value: Any) =>
    new IllegalArgumentException(s"Cannot convert: $value to Boolean")

  override type ResultType = JBoolean
  override val resultTypeClass: Class[JBoolean] = classOf[JBoolean]

  override def canConvert(value: Any): JBoolean = convertToBoolean(value).isRight

  override def convert(value: Any): JBoolean = convertToBoolean(value) match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  override def convertOrNull(value: Any): JBoolean = convertToBoolean(value) match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def convertToBoolean(value: Any): Either[Throwable, JBoolean] = value match {
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

object ToStringConversion extends Conversion {
  override type ResultType = String
  override val resultTypeClass: Class[ResultType] = classOf[String]

  override def canConvert(value: Any): JBoolean      = true
  override def convert(value: Any): ResultType       = value.toString
  override def convertOrNull(value: Any): ResultType = value.toString
}

object ToMapConversion extends Conversion {
  private[extension] val keyName          = "key"
  private[extension] val valueName        = "value"
  private[extension] val keyAndValueNames = JSet.of(keyName, valueName)

  override type ResultType = JMap[_, _]
  override val resultTypeClass: Class[JMap[_, _]] = classOf[ResultType]
  override def typingResult: TypingResult         = Typed.genericTypeClass(resultTypeClass, List(Unknown, Unknown))

  override def canConvert(value: Any): JBoolean = value match {
    case _: JMap[_, _]     => true
    case c: JCollection[_] => canConvertToMap(c)
    case a: Array[_]       => canConvertToMap(ConversionHandler.convertArrayToList(a))
    case _                 => false
  }

  override def convert(value: Any): ResultType = convertToMap[Any, Any](value) match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  override def convertOrNull(value: Any): ResultType = convertToMap[Any, Any](value) match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def convertToMap[K, V](value: Any): Either[Throwable, JMap[K, V]] =
    value match {
      case m: JMap[K, V] @unchecked => Right(m)
      case a: Array[_]              => convertToMap[K, V](ConversionHandler.convertArrayToList(a))
      case c: JCollection[JMap[_, _] @unchecked] if canConvertToMap(c) =>
        val map = new JHashMap[K, V]()
        c.forEach(e => map.put(e.get(keyName).asInstanceOf[K], e.get(valueName).asInstanceOf[V]))
        Right(map)
      case x => Left(new IllegalArgumentException(s"Cannot convert: $x to a Map"))
    }

  private def canConvertToMap(c: JCollection[_]): Boolean = c.isEmpty || c
    .stream()
    .allMatch {
      case m: JMap[_, _] if !m.isEmpty => m.keySet().containsAll(keyAndValueNames)
      case _                           => false
    }

}

object ToListConversion extends Conversion {
  private val collectionClass = classOf[JCollection[_]]

  override type ResultType = JList[_]
  override val resultTypeClass: Class[JList[_]] = classOf[JList[_]]
  override def typingResult: TypingResult       = Typed.genericTypeClass(resultTypeClass, List(Unknown))

  override def canConvert(value: Any): JBoolean = value.getClass.isAOrChildOf(collectionClass) || value.getClass.isArray

  override def convert(value: Any): ResultType = convertToList[Any](value) match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  override def convertOrNull(value: Any): ResultType = convertToList[Any](value) match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def convertToList[T](value: Any): Either[Throwable, JList[T]] = {
    value match {
      case l: JList[T @unchecked]       => Right(l)
      case c: JCollection[T @unchecked] => Right(new JArrayList[T](c))
      case a: Array[T @unchecked]       => Right(ConversionHandler.convertArrayToList(a).asInstanceOf[JList[T]])
      case x                            => Left(new IllegalArgumentException(s"Cannot convert: $x to a List"))
    }
  }

}
