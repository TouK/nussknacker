package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.generics.GenericType
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.util.functions.ConversionUtils.{stringToBigInteger, stringToBoolean}
import pl.touk.nussknacker.engine.util.functions.NumericUtils.ToNumberTypingFunction
import pl.touk.nussknacker.engine.util.json.{JsonUtils, ToJsonEncoder}

import scala.util.Try

object conversion extends ConversionUtils

trait ConversionUtils extends HideToString {

  @Documentation(description =
    "Wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive"
  )
  def toAny(@ParamName("value") value: Any): Any = {
    value
  }

  @Documentation(description = "Parse string to number")
  @GenericType(typingFunction = classOf[ToNumberTypingFunction])
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number =
    numeric.toNumber(stringOrNumber)

  @Documentation(description = "Parse any value to Number or null in case failure")
  def toNumberOrNull(@ParamName("value") value: Any): java.lang.Number = value match {
    case v: String => Try(numeric.toNumber(v)).getOrElse(null)
    case v: Number => v
    case _         => null
  }

  @Documentation(description = "Convert any value to String")
  def toString(@ParamName("value") value: Any): java.lang.String = value match {
    case v: String => v
    case null      => null
    case v         => v.toString
  }

  @Documentation(description = "Convert any value to Boolean or throw exception in case of failure")
  def toBoolean(@ParamName("value") value: Any): java.lang.Boolean = value match {
    case v: String =>
      stringToBoolean(v).getOrElse {
        throw new IllegalArgumentException(s"Cannot convert: $value to Boolean")
      }
    case v: java.lang.Boolean => v
    case null                 => null
    case _                    => throw new IllegalArgumentException(s"Cannot convert: $value to Boolean")
  }

  @Documentation(description = "Convert any value to Boolean or throw exception in case of failure")
  def toBooleanOrNull(@ParamName("value") value: Any): java.lang.Boolean = value match {
    case v: String            => stringToBoolean(v).orNull
    case v: java.lang.Boolean => v
    case _                    => null
  }

  @Documentation(description = "Convert any value to Integer or throw exception in case of failure")
  def toInteger(@ParamName("value") value: Any): java.lang.Integer = value match {
    case v: String => Integer.valueOf(numeric.toNumber(v).intValue())
    case v: Number => v.intValue()
    case null      => null
    case _         => throw new IllegalArgumentException(s"Cannot convert: $value to Integer")
  }

  @Documentation(description = "Convert any value to Integer or null in case of failure")
  def toIntegerOrNull(@ParamName("value") value: Any): java.lang.Integer = value match {
    case v: String => Try(Integer.valueOf(numeric.toNumber(v).intValue())).getOrElse(null)
    case v: Number => v.intValue()
    case _         => null
  }

  @Documentation(description = "Convert any value to Long or throw exception in case of failure")
  def toLong(@ParamName("value") value: Any): java.lang.Long = value match {
    case v: String => java.lang.Long.valueOf(numeric.toNumber(v).longValue())
    case v: Number => v.longValue()
    case null      => null
    case _         => throw new IllegalArgumentException(s"Cannot convert: $value to Long")
  }

  @Documentation(description = "Convert any value to Long or null in case of failure")
  def toLongOrNull(@ParamName("value") value: Any): java.lang.Long = value match {
    case v: String => Try(java.lang.Long.valueOf(numeric.toNumber(v).longValue())).getOrElse(null)
    case v: Number => v.longValue()
    case _         => null
  }

  @Documentation(description = "Convert any value to Double or throw exception in case of failure")
  def toDouble(@ParamName("value") value: Any): java.lang.Double = value match {
    case v: String => java.lang.Double.valueOf(numeric.toNumber(v).doubleValue())
    case v: Number => v.doubleValue()
    case null      => null
    case _         => throw new IllegalArgumentException(s"Cannot convert: $value to Double")
  }

  @Documentation(description = "Convert any value to Double or null in case of failure")
  def toDoubleOrNull(@ParamName("value") value: Any): java.lang.Double = value match {
    case v: String => Try(java.lang.Double.valueOf(numeric.toNumber(v).doubleValue())).getOrElse(null)
    case v: Number => v.doubleValue()
    case _         => null
  }

  @Documentation(description = "Convert any value to BigInteger or throw exception in case of failure")
  def toBigInteger(@ParamName("value") value: Any): java.math.BigInteger = value match {
    case v: String               => stringToBigInteger(v)
    case v: java.math.BigInteger => v
    case v: java.math.BigDecimal => v.toBigInteger
    case v: Number               => java.math.BigInteger.valueOf(v.longValue())
    case null                    => null
    case _                       => throw new IllegalArgumentException(s"Cannot convert: $value to BigInteger")
  }

  @Documentation(description = "Convert any value to BigInteger or null in case of failure")
  def toBigIntegerOrNull(@ParamName("value") value: Any): java.math.BigInteger = value match {
    case v: String               => Try(stringToBigInteger(v)).getOrElse(null)
    case v: java.math.BigInteger => v
    case v: java.math.BigDecimal => v.toBigInteger
    case v: Number               => java.math.BigInteger.valueOf(v.longValue())
    case _                       => null
  }

  @Documentation(description = "Convert any value to BigDecimal or throw exception in case of failure")
  def toBigDecimal(@ParamName("value") value: Any): java.math.BigDecimal = value match {
    case v: String               => new java.math.BigDecimal(v)
    case v: java.math.BigInteger => new java.math.BigDecimal(v)
    case v: java.math.BigDecimal => v
    case v: Number               => new java.math.BigDecimal(v.toString)
    case null                    => null
    case _                       => throw new IllegalArgumentException(s"Cannot convert: $value to BigDecimal")
  }

  @Documentation(description = "Convert any value to BigDecimal or null in case of failure")
  def toBigDecimalOrNull(@ParamName("value") value: Any): java.math.BigDecimal = value match {
    case v: String               => Try(new java.math.BigDecimal(v)).getOrElse(null)
    case v: java.math.BigInteger => new java.math.BigDecimal(v)
    case v: java.math.BigDecimal => v
    case v: Number               => Try(new java.math.BigDecimal(v.toString)).getOrElse(null)
    case _                       => null
  }

  @Documentation(description = "Convert String value to JSON")
  def toJson(@ParamName("value") value: String): Any = {
    toJsonEither(value).toTry.get
  }

  @Documentation(description = "Convert String value to JSON or null in case of failure")
  def toJsonOrNull(@ParamName("value") value: String): Any = {
    toJsonEither(value).getOrElse(null)
  }

  @Documentation(description = "Convert JSON to String")
  def toJsonString(@ParamName("value") value: Any): String = {
    jsonEncoder.encode(value).noSpaces
  }

  private def toJsonEither(value: String): Either[Throwable, Any] = {
    io.circe.parser.parse(value) match {
      case Right(json) => Right(JsonUtils.jsonToAny(json))
      case Left(ex)    => Left(new IllegalArgumentException(s"Cannot convert [$value] to JSON", ex))
    }
  }

  private lazy val jsonEncoder = new ToJsonEncoder(true, this.getClass.getClassLoader)

}

object ConversionUtils {

  private def stringToBigInteger(value: String): java.math.BigInteger =
    numeric.toNumber(value) match {
      case n: java.math.BigInteger => n
      case n                       => java.math.BigInteger.valueOf(n.longValue())
    }

  private def stringToBoolean(value: String): Option[java.lang.Boolean] =
    if ("true".equalsIgnoreCase(value)) {
      Some(true)
    } else if ("false".equalsIgnoreCase(value)) {
      Some(false)
    } else {
      None
    }

}
