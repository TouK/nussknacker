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

  private val additionalEncoders: PartialFunction[Any, Json] = { case value: DisplayJson => value.asJson }

  def encode(obj: Any): Json = optionalCustomisations
    .foldLeft(highPriority.orElse(additionalEncoders))(_.orElse(_))
    .applyOrElse(
      obj,
      (any: Any) =>
        ToJsonEncoderWithFallback.encodeValue(any, encodeOpt) match {
          case Validated.Valid(json) =>
            json
          case Validated.Invalid(_) =>
            if (failOnUnknown) {
              throw new IllegalArgumentException(s"Invalid type: ${obj.getClass}")
            } else {
              fromString(any.toString)
            }
        }
    )

  private def encodeOpt(obj: Any): Option[Json] =
    Try(encode(obj)).toOption

}
