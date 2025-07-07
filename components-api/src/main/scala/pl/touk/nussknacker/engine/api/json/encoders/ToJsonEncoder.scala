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

  // The default behavior is to fail-fast. This approach helps in an investigation of a potential wrong type usage in the json context.
  val default = new StrictToJsonEncoder(defaultClassloader)

  // For this encoder, encodeUnsafe() doesn't throw exceptions for unknown values - as a fallback .toString method is used
  // encode() method is hidden because it always returns Valid
  val looseEncoder = new LooseToJsonEncoder(getClass.getClassLoader)

}

class StrictToJsonEncoder(override protected val classLoader: ClassLoader) extends ToJsonEncoder {

  // This method has sense only for strict encoder - for loose, it will always return Valid
  def encode(obj: Any): ValidatedNel[String, Json] = doEncode(obj)

  override protected def handleUnknownValue(any: Any): Option[Json] = None

}

class LooseToJsonEncoder(override protected val classLoader: ClassLoader) extends ToJsonEncoder {

  override protected def handleUnknownValue(any: Any): Option[Json] = Some(fromString(any.toString))

}

sealed trait ToJsonEncoder {

  protected val classLoader: ClassLoader

  private val optionalCustomisations =
    ServiceLoader.load(classOf[ToJsonEncoderCustomisation], classLoader).asScala.map(_.encoder(this.encodeUnsafe))

  // Used by external project
  protected val highPriority: PartialFunction[Any, Json] = PartialFunction.empty

  private lazy val customEncodingPF = optionalCustomisations.foldLeft(highPriority)(_.orElse(_))

  private val encoderWithFallback = new ToJsonEncoderWithFallback {
    override protected def handleUnknownValue(any: Any): Option[Json] = {
      customEncoding(any).orElse(ToJsonEncoder.this.handleUnknownValue(any))
    }
  }

  protected def handleUnknownValue(any: Any): Option[Json]

  def encodeUnsafe(obj: Any): Json =
    doEncode(obj).getOrElse {
      throw new IllegalArgumentException(s"Invalid type: ${obj.getClass}")
    }

  // This method is protected because it has no sense for LooseToJsonEncoder
  protected def doEncode(obj: Any): ValidatedNel[String, Json] =
    customEncoding(obj).map(Valid(_)).getOrElse(encoderWithFallback.encodeValue(obj))

  private def customEncoding(obj: Any): Option[Json] = {
    if (customEncodingPF.isDefinedAt(obj)) {
      Try(customEncodingPF.apply(obj)).toOption
    } else {
      None
    }
  }

}
