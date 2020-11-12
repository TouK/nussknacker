package pl.touk.nussknacker.engine.flink.api.serialization

import com.esotericsoftware.kryo.Serializer

abstract class SerializerWithSpecifiedClass[T](acceptsNull: Boolean, immutable: Boolean)
  extends Serializer[T](acceptsNull, immutable) with Serializable {

  def clazz: Class[_]

}
