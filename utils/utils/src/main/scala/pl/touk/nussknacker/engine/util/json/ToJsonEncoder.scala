package pl.touk.nussknacker.engine.util.json

import cats.data.Validated
import io.circe.Json
import io.circe.Json._
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoderWithFallback

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.util.Try

object ToJsonEncoder {

  val defaultForTests: ToJsonEncoder = ToJsonEncoder(failOnUnknown = true, getClass.getClassLoader)

}

case class ToJsonEncoder(
    failOnUnknown: Boolean,
    classLoader: ClassLoader,
    highPriority: PartialFunction[Any, Json] = Map()
) {

  private val optionalCustomisations =
    ServiceLoader.load(classOf[ToJsonEncoderCustomisation], classLoader).asScala.map(_.encoder(this.encode))

  private val additionalFallbackEncoders: PartialFunction[Any, Json] = {
    // DisplayJson is not visible at the components-api level, therefore its handling needs to be added here
    case value: DisplayJson =>
      value.asJson
    // fixme: Some numeric utils and helpers rely on the behavior,
    //   where ToJsonEncoderWithFallback cannot decode java.lang.Number.
    //   The decoding of Number could not have been therefore moved to ToJsonEncoderWithFallback
    case value: Number =>
      fromDoubleOrNull(value.doubleValue())
    // fixme: Some SpEL behavior rely on the fact, that ToJsonEncoderWithFallback cannot decode scala Array.
    //   The decoding of Array could not have been therefore moved to ToJsonEncoderWithFallback
    case vals: Array[_] =>
      fromValues(vals.map(encode))
  }

  def encode(obj: Any): Json =
    customEncoding(obj)
      .getOrElse(
        ToJsonEncoderWithFallback.encodeValue(obj, fallback) match {
          case Validated.Valid(json: Json) =>
            json
          case Validated.Invalid(_) =>
            if (failOnUnknown) {
              throw new IllegalArgumentException(s"Invalid type: ${obj.getClass}")
            } else {
              fromString(obj.toString)
            }
        }
      )

  private def customEncoding(obj: Any): Option[Json] = {
    val customEncodingPF = optionalCustomisations.foldLeft(highPriority)(_.orElse(_))
    if (customEncodingPF.isDefinedAt(obj)) {
      Try(customEncodingPF.apply(obj)).toOption
    } else {
      None
    }
  }

  private def fallback(any: Any): Option[Json] = {
    customEncoding(any) match {
      case Some(value) =>
        Some(value)
      case None if additionalFallbackEncoders.isDefinedAt(any) =>
        Some(additionalFallbackEncoders(any))
      case None if !failOnUnknown =>
        Some(fromString(any.toString))
      case None =>
        None
    }
  }

}
