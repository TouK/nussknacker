package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.common.serialization.Serializer

import java.nio.charset.StandardCharsets
import java.util

class CharSequenceSerializer extends Serializer[AnyRef] {

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def serialize(topic: String, data: AnyRef): Array[Byte] = {
    data match {
      case charSeq: CharSequence => charSeq.toString.getBytes(StandardCharsets.UTF_8)
      case null => null
      case _ => throw new IllegalArgumentException(s"Unexpected key class: ${data.getClass}. Should be CharSequence")
    }
  }

  override def close(): Unit = {}

}
