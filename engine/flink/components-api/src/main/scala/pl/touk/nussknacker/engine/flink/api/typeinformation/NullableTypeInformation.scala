package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.runtime.NullableSerializer

import scala.annotation.nowarn

class NullableTypeInformation[T](private val inner: TypeInformation[T]) extends TypeInformation[T] {

  override def isBasicType: Boolean = inner.isBasicType

  override def isTupleType: Boolean = inner.isTupleType

  override def getArity: Int = inner.getArity

  override def getTotalFields: Int = inner.getTotalFields

  override def getTypeClass: Class[T] = inner.getTypeClass

  override def isKeyType: Boolean = inner.isKeyType

  // TODO: Remove after upgrade to Flink 2.x
  @nowarn("cat=deprecation")
  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    createSerializer(config.getSerializerConfig)

  override def createSerializer(config: SerializerConfig): TypeSerializer[T] =
    NullableSerializer
      .wrapIfNullIsNotSupported(inner.createSerializer(config), false)

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[NullableTypeInformation[_]]

  override def equals(obj: Any): Boolean = obj match {
    case nullableTypeInfo: NullableTypeInformation[_] => inner.equals(nullableTypeInfo.inner)
    case _                                            => false
  }

  override def hashCode(): Int = inner.hashCode()

  override def toString: String = s"Nullable($inner)"

}

object NullableTypeInformation {

  def wrap[T](inner: TypeInformation[T]): NullableTypeInformation[T] =
    new NullableTypeInformation[T](inner)

}
