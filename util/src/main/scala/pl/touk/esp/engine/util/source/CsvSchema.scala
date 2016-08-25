package pl.touk.esp.engine.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.util.serialization.DeserializationSchema

class CsvSchema[T: TypeInformation](constructor: List[String] => T) extends DeserializationSchema[T] {
  override def isEndOfStream(t: T): Boolean = false

  override def deserialize(bytes: Array[Byte]): T = constructor(toFields(bytes))

  def toFields(bytes: Array[Byte]) = new String(bytes).split("\\|").toList

  override def getProducedType: TypeInformation[T] = implicitly[TypeInformation[T]]
}