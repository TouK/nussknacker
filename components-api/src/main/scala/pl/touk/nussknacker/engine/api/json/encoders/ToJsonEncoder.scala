package pl.touk.nussknacker.engine.api.json.encoders

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import io.circe.Json
import io.circe.Json._
import pl.touk.nussknacker.engine.util.json.ToJsonEncoderCustomisation

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.util.Try

object ToJsonEncoder {

  // We assume that this classloader is the model classloader
  private val defaultClassloader = getClass.getClassLoader

  // The default behaviour is to fail-fast. This approach helps in an investigation of a potential wrong type usage in the json context.
  val default = new StrictToJsonEncoder(defaultClassloader)

  // For this encoder, encodeUnsafe() doesn't throw exceptions for unknown values - as a fallback .toString method is used
  // encode() method is hidden because it always returns Valid
  val looseEncoder = new LooseToJsonEncoder(getClass.getClassLoader)

}

class StrictToJsonEncoder(classLoader: ClassLoader) extends ToJsonEncoder(failOnUnknown = true, classLoader) {
  // Unhides encode method, which is hidden for loose encoder
  override def encode(obj: Any): ValidatedNel[String, Json] = super.encode(obj)
}

class LooseToJsonEncoder(classLoader: ClassLoader) extends ToJsonEncoder(failOnUnknown = false, classLoader)

class ToJsonEncoder(failOnUnknown: Boolean, classLoader: ClassLoader) {

  private val optionalCustomisations =
    ServiceLoader.load(classOf[ToJsonEncoderCustomisation], classLoader).asScala.map(_.encoder(this.encodeUnsafe))

  private val encoderWithFallback = new ToJsonEncoderWithFallback {
    override protected def handleUnknownValue(any: Any): Option[Json] = {
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

  // Used by external project
  protected val highPriority: PartialFunction[Any, Json] = PartialFunction.empty

  def encodeUnsafe(obj: Any): Json = {
    encode(obj).getOrElse {
      if (failOnUnknown) {
        throw new IllegalArgumentException(s"Invalid type: ${obj.getClass}")
      } else {
        fromString(obj.toString)
      }
    }
  }

  protected def encode(obj: Any): ValidatedNel[String, Json] =
    customEncoding(obj).map(Valid(_)).getOrElse(encoderWithFallback.encodeValue(obj))

  private def customEncoding(obj: Any): Option[Json] = {
    val customEncodingPF = optionalCustomisations.foldLeft(highPriority)(_.orElse(_))
    if (customEncodingPF.isDefinedAt(obj)) {
      Try(customEncodingPF.apply(obj)).toOption
    } else {
      None
    }
  }

}
