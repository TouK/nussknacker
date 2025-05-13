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

  private val additionalEncoders: PartialFunction[Any, Json] = { case value: DisplayJson =>
    value.asJson
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
    val customEncodingPF = optionalCustomisations.foldLeft(highPriority.orElse(additionalEncoders))(_.orElse(_))
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
      case None if !failOnUnknown =>
        Some(fromString(any.toString))
      case None =>
        None
    }
  }

}
