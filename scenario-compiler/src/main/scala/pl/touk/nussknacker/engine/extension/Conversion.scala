package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
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
  type ResultType >: Null <: AnyRef
  val resultTypeClass: Class[ResultType]

  def convertEither(value: Any): Either[Throwable, ResultType]
  def applies(clazz: Class[_]): Boolean

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
}

sealed trait NumericConversion extends Conversion {
  private val numberClass  = classOf[JNumber]
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]

  override def applies(clazz: Class[_]): Boolean =
    clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass
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

object ToLongConversion extends NumericConversion {
  override type ResultType = JLong
  override val resultTypeClass: Class[JLong] = classOf[JLong]

  override def convertEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Right(v.longValue())
      case v: String => Try(JLong.valueOf(toNumber(v).longValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

}

object ToDoubleConversion extends NumericConversion {
  override type ResultType = JDouble
  override val resultTypeClass: Class[JDouble] = classOf[JDouble]

  override def convertEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(v.doubleValue())
      case v: String => Try(JDouble.valueOf(toNumber(v).doubleValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

}

object ToBigDecimalConversion extends NumericConversion {
  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override def convertEither(value: Any): Either[Throwable, JBigDecimal] =
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
  private val allowedClassesForConversion: Set[Class[_]] = Set(classOf[String], classOf[Object])

  override type ResultType = JBoolean
  override val resultTypeClass: Class[JBoolean] = classOf[JBoolean]

  override def applies(clazz: Class[_]): Boolean = allowedClassesForConversion.contains(clazz)

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

object ToStringConversion extends Conversion {
  override type ResultType = String
  override val resultTypeClass: Class[ResultType]                   = classOf[String]
  override def applies(clazz: Class[_]): Boolean                    = true
  override def convertEither(value: Any): Either[Throwable, String] = Right(value.toString)
}

sealed trait CollectionConversion extends Conversion {
  private val unknownClass    = classOf[Object]
  private val collectionClass = classOf[JCollection[_]]

  override def applies(clazz: Class[_]): Boolean =
    clazz.isAOrChildOf(collectionClass) || clazz == unknownClass || clazz.isArray
}

object ToMapConversion extends CollectionConversion {
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

  override def convertEither(value: Any): Either[Throwable, JMap[_, _]] =
    value match {
      case m: JMap[_, _] => Right(m)
      case a: Array[_]   => convertEither(ConversionHandler.convertArrayToList(a))
      case c: JCollection[JMap[_, _] @unchecked] if canConvertToMap(c) =>
        val map = new JHashMap[Any, Any]()
        c.forEach(e => map.put(e.get(keyName), e.get(valueName)))
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

object ToListConversion extends CollectionConversion {
  private val collectionClass = classOf[JCollection[_]]

  override type ResultType = JList[_]
  override val resultTypeClass: Class[JList[_]] = classOf[JList[_]]
  override def typingResult: TypingResult       = Typed.genericTypeClass(resultTypeClass, List(Unknown))

  override def canConvert(value: Any): JBoolean = value.getClass.isAOrChildOf(collectionClass) || value.getClass.isArray

  override def convertEither(value: Any): Either[Throwable, JList[_]] = {
    value match {
      case l: JList[_]       => Right(l)
      case c: JCollection[_] => Right(new JArrayList[Any](c))
      case a: Array[_]       => Right(ConversionHandler.convertArrayToList(a))
      case x                 => Left(new IllegalArgumentException(s"Cannot convert: $x to a List"))
    }
  }

}
