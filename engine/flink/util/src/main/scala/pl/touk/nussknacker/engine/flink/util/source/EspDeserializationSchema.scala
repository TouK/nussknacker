package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.serialization.DeserializationSchema

class EspDeserializationSchema[T: TypeInformation](converter: Array[Byte] => T) extends DeserializationSchema[T] {
  override def isEndOfStream(t: T): Boolean = false

  override def deserialize(bytes: Array[Byte]): T = {
    converter(bytes)
  }

  override def getProducedType: TypeInformation[T] = implicitly[TypeInformation[T]]
}