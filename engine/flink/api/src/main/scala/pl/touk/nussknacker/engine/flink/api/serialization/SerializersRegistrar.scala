package pl.touk.nussknacker.engine.flink.api.serialization

import org.apache.flink.api.common.ExecutionConfig

trait SerializersRegistrar {

  def register(config: ExecutionConfig): Unit

  protected def registerSerializer(config: ExecutionConfig)(serializer: SerializerWithSpecifiedClass[_]): Unit = {
    config.getRegisteredTypesWithKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
    config.getDefaultKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
  }

}

trait BaseSerializersRegistrar extends SerializersRegistrar {

  protected def serializers: List[SerializerWithSpecifiedClass[_]]

  override def register(config: ExecutionConfig): Unit = {
    val registers = registerSerializer(config) _
    serializers.foreach(registers)
  }

}
