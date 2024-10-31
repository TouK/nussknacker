package pl.touk.nussknacker.engine.flink.api.serialization

import com.esotericsoftware.kryo.Serializer
import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig

trait SerializerRegistrar[S <: Serializer[_]] {

  def registerIn(config: ExecutionConfig): Unit

}

@deprecated(
  "Instance-based Kryo serializers registration is deprecated and will be removed in Flink 2.0. " +
    "Instead of this either standard Flink classes should be passed or TypeInfo mechanism or class-based Kryo serializers should be used. " +
    "See https://cwiki.apache.org/confluence/display/FLINK/FLIP-398:+Improve+Serialization+Configuration+And+Usage+In+Flink and " +
    "deprecation notice next to SerializableSerializer for details",
  "1.18"
)
class InstanceBasedKryoSerializerRegistrar[T, S <: Serializer[T] with Serializable](
    serializerInstance: S,
    clazz: Class[T]
) extends SerializerRegistrar[S] {

  @silent("deprecated")
  override def registerIn(config: ExecutionConfig): Unit = {
    val serializableSerializer = new ExecutionConfig.SerializableSerializer(serializerInstance)
    config.getRegisteredTypesWithKryoSerializers.put(clazz, serializableSerializer)
    config.getDefaultKryoSerializers.put(clazz, serializableSerializer)
  }

}

@silent("deprecated")
class ClassBasedKryoSerializerRegistrar[T, S <: Serializer[T]](serializerClass: Class[S], clazz: Class[T])
    extends SerializerRegistrar[S] {

  override def registerIn(config: ExecutionConfig): Unit = {
    config.addDefaultKryoSerializer(clazz, serializerClass)
  }

}
