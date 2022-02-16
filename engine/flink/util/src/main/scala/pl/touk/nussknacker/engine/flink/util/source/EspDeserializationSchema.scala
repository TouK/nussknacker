package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.serialization.AbstractDeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation

class EspDeserializationSchema[T: TypeInformation](converter: Array[Byte] => T) extends AbstractDeserializationSchema[T](implicitly[TypeInformation[T]]) {
  override def deserialize(bytes: Array[Byte]): T = {
    converter(bytes)
  }
}