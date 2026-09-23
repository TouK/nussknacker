package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.{AtomicType, TypeInformation}
import org.apache.flink.api.common.typeutils.{TypeComparator, TypeSerializer}
import org.apache.flink.api.java.typeutils.runtime.NullableSerializer

/**
 * Decorates another [[TypeInformation]], adding null support to the serializer it creates
 */
class NullableTypeInfo[T](val delegate: TypeInformation[T]) extends TypeInformation[T] with AtomicType[T] {

  override def isBasicType: Boolean = delegate.isBasicType

  override def isTupleType: Boolean = delegate.isTupleType

  override def getArity: Int = delegate.getArity

  override def getTotalFields: Int = delegate.getTotalFields

  override def getTypeClass: Class[T] = delegate.getTypeClass

  override def isKeyType: Boolean = delegate.isKeyType

  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    createSerializer(config.getSerializerConfig)

  override def createSerializer(config: SerializerConfig): TypeSerializer[T] =
    NullableSerializer.wrapIfNullIsNotSupported(delegate.createSerializer(config), false)

  override def createComparator(ascending: Boolean, executionConfig: ExecutionConfig): TypeComparator[T] =
    delegate match {
      case atomic: AtomicType[T @unchecked] => atomic.createComparator(ascending, executionConfig)
      case other => throw new UnsupportedOperationException(s"$other does not support creating a comparator")
    }

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[NullableTypeInfo[_]]

  override def equals(obj: Any): Boolean = obj match {
    case other: NullableTypeInfo[_] => other.canEqual(this) && delegate == other.delegate
    case _                          => false
  }

  override def hashCode(): Int = delegate.hashCode()

  override def toString: String = s"Nullable[$delegate]"

}
