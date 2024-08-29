package pl.touk.nussknacker.engine.flink.api.serialization

import com.esotericsoftware.kryo.Serializer
import org.apache.flink.api.common.ExecutionConfig

abstract class SerializerWithSpecifiedClass[T](acceptsNull: Boolean, immutable: Boolean)
    extends Serializer[T](acceptsNull, immutable)
    with Serializable {

  def clazz: Class[_]

  def registerIn(config: ExecutionConfig): Unit = {
    // FIXME: replace with external class adding serializer class
//    config.getRegisteredTypesWithKryoSerializers.put(clazz, new ExecutionConfig.SerializableSerializer(this))
//    config.getDefaultKryoSerializers.put(clazz, new ExecutionConfig.SerializableSerializer(this))
    config.addDefaultKryoSerializer(clazz, getClass)
  }

}
