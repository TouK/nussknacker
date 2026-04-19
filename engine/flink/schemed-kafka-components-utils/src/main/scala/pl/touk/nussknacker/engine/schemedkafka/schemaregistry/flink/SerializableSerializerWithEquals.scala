package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.Serializer
import org.apache.flink.api.common.SerializableSerializer

import java.util.Objects

/**
 * Allows to use an instance-base serializer with `RawType` in table-api components, which requires a working `equals`:
 * - `RawType.equals` checks `serializer.equals(rawType.serializer)`
 * - `KryoSerializer.equals` checks `Objects.equals(defaultSerializers, other.defaultSerializers)`
 * - `KryoSerializer.defaultSerializers` is a `LinkedHashMap<Class<?>, ExecutionConfig.SerializableSerializer<?>>`
 * - `SerializableSerializer` has an `equals` method not implemented (so it checks reference equality)
 */
class SerializableSerializerWithEquals[T <: Serializer[?] with Serializable](serializer: T)
  extends SerializableSerializer[T](serializer) {

  override def hashCode(): Int = Objects.hashCode(getSerializer)

  override def equals(obj: Any): Boolean = obj match {
    case other: SerializableSerializerWithEquals[T] => other.getSerializer.equals(getSerializer)
    case _                        => false
  }

  override def toString = s"SerializableSerializerWithEquals($getSerializer)"

}
