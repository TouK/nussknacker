package pl.touk.nussknacker.engine.kafka

import io.circe.Decoder
import pl.touk.nussknacker.engine.api.CirceUtil

import java.nio.charset.StandardCharsets
import scala.reflect.{ClassTag, classTag}

object ConsumerRecordHelper {

  def as[T: Decoder : ClassTag](data: Array[Byte]): T = {
    val clazz = classTag[T].runtimeClass

    if (classOf[String].isAssignableFrom(clazz)) {
      Option(data).map(value => new String(value, StandardCharsets.UTF_8)).orNull.asInstanceOf[T]
    } else {
      CirceUtil.decodeJsonUnsafe[T](data)
    }
  }

}
