package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer

import scala.reflect.{classTag, ClassTag}

class SimpleTypeInformation[T: ClassTag](typeSerializer: TypeSerializer[T]) extends TypeInformation[T] {

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def isKeyType: Boolean = false

  override def getTypeClass: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]

  override def createSerializer(config: SerializerConfig): TypeSerializer[T] =
    typeSerializer

  override def hashCode(): Int = getTypeClass.hashCode()

  override def canEqual(obj: Any): Boolean = obj.getClass == getClass

  override def equals(obj: Any): Boolean = obj match {
    case simple: SimpleTypeInformation[_] => simple.getTypeClass == getTypeClass
    case _                                => false
  }

  override def toString: String = getTypeClass.getSimpleName

}
