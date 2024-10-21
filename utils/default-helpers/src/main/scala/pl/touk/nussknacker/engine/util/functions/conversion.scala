package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.generics.GenericType
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
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
