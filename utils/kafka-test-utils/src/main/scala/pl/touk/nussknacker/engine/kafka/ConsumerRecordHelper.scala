package pl.touk.nussknacker.engine.kafka

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil

import java.nio.charset.StandardCharsets
import scala.reflect.{ClassTag, classTag}

object ConsumerRecordHelper {

  // General helper's method - don't remove it
  def asBytes[T: Encoder: ClassTag](value: T): Array[Byte] =
    value match {
      case null => null
      case _    => asString(value).getBytes(StandardCharsets.UTF_8)
    }

  def asString[T: Encoder: ClassTag](value: T): String =
    value match {
      case null        => null
      case str: String => str
      case _           => implicitly[Encoder[T]].apply(value).noSpaces
    }

  def asJson[T: Decoder: ClassTag](value: Array[Byte]): T = {
    val clazz = classTag[T].runtimeClass

    if (classOf[String].isAssignableFrom(clazz)) {
      Option(value).map(value => new String(value, StandardCharsets.UTF_8)).orNull.asInstanceOf[T]
    } else {
      CirceUtil.decodeJsonUnsafe[T](value)
    }
  }

}
