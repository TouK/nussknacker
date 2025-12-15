package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}
import pl.touk.nussknacker.engine.api.generics.GenericType
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonSimpleDecoder
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.util.functions.NumericUtils.ToNumberTypingFunction

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
  def toNumber(@ParamName("stringOrNumber") stringOrNumber: Any): java.lang.Number =
    numeric.toNumber(stringOrNumber)

  // TODO 1: We should probable rename toJson/toJsonOrNull to parseJson/parseJsonOrNull
  // TODO 2: We should extract a separate helper JSON and shorten parseJson/parseJsonOrNull to just parse/parseOrNull
  // TODO 3: toJsonString can be a extension method like other toXYZ methods
  // TODO 4: We should consider exposing Circe's Json class or similar interface. Thanks to that user could do: #value.toJson.spaces2
  @Documentation(description = "Parse String value as JSON")
  def toJson(@ParamName("value") value: String): Any = {
    parseJsonEither(value).toTry.get
  }

  @Documentation(description = "Parse String value as JSON. It returns null in case of failure")
  def toJsonOrNull(@ParamName("value") value: String): Any = {
    parseJsonEither(value).getOrElse(null)
  }

  @Documentation(description = "Convert value to JSON String")
  def toJsonString(@ParamName("value") value: Any): String = {
    ToJsonEncoder.default.encodeUnsafe(value).noSpaces
  }

  private def parseJsonEither(value: String): Either[Throwable, Any] = {
    io.circe.parser.parse(value) match {
      case Right(json) => Right(FromJsonSimpleDecoder.jsonToAny(json))
      case Left(ex)    => Left(new IllegalArgumentException(s"Cannot convert [$value] to JSON", ex))
    }
  }

}
