package pl.touk.nussknacker.engine.kafka

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

object ConsumerRecordHelper {

  def asBytes[T: Encoder: ClassTag](data: T): Array[Byte] =
    asStr(data).getBytes(StandardCharsets.UTF_8)

  def asStr[T: Encoder: ClassTag](data: T): String =
    data match {
      case str: String => str
      case value       => implicitly[Encoder[T]].apply(value).noSpaces
    }

  def asJson[T: Decoder: ClassTag](data: Array[Byte]): T = {
    val clazz = scala.reflect.classTag[T].runtimeClass

    if (classOf[String].isAssignableFrom(clazz)) {
      Option(data).map(value => new String(value, StandardCharsets.UTF_8)).orNull.asInstanceOf[T]
    } else {
      CirceUtil.decodeJsonUnsafe[T](data)
    }
  }

}
