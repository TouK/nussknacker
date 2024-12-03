package pl.touk.nussknacker.engine.util.functions

import com.github.ghik.silencer.silent
import pl.touk.nussknacker.engine.api.generics.GenericType
import pl.touk.nussknacker.engine.api.json.FromJsonDecoder
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.util.functions.NumericUtils.ToNumberTypingFunction
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

object conversion extends ConversionUtils

trait ConversionUtils extends HideToString {

  @Documentation(description =
    "Wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive"
  )
  def toAny(@ParamName("value") value: Any): Any = {
    value
  }

  // TODO - remove in 1.19
  @Documentation(description = "Deprecated - will be removed in 1.19")
  @GenericType(typingFunction = classOf[ToNumberTypingFunction])
  @silent("deprecated")
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number =
    numeric.toNumber(stringOrNumber)

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
      case Right(json) => Right(FromJsonDecoder.jsonToAny(json))
      case Left(ex)    => Left(new IllegalArgumentException(s"Cannot convert [$value] to JSON", ex))
    }
  }

  private lazy val jsonEncoder = new ToJsonEncoder(true, this.getClass.getClassLoader)

}
