package pl.touk.nussknacker.engine.avro.schema

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}

import scala.util.{Failure, Properties, Success, Try}

object AvroStringSettings extends LazyLogging {
  val default: Boolean = true
  val envName = "AVRO_USE_STRING_FOR_STRING_TYPE"

  lazy val forceUsingStringForStringSchema: Boolean = Properties.envOrNone(envName)
    .map(str => Try(str.toBoolean) match {
      case Failure(cause) =>
        throw new RuntimeException(s"Environment variable $envName=$str is not valid boolean value", cause)
      case Success(value) => value
    }).getOrElse(default)

  lazy val stringTypingResult: TypedClass = if (forceUsingStringForStringSchema) Typed.typedClass[String] else Typed.typedClass[CharSequence]
}
