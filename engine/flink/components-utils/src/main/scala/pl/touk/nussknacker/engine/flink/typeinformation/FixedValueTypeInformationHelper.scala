package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.BasicTypeInfo
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.util.function.Supplier
import scala.reflect.ClassTag

abstract class FixedValueTypeInformationHelper[T] extends TypeSerializerSingleton[T] {

  def value: T

  override def isImmutableType: Boolean = true

  override def createInstance(): T = value

  override def copy(from: T): T = value

  override def copy(from: T, reuse: T): T = value

  override def getLength: Int = 0

  override def serialize(record: T, target: DataOutputView): Unit = {}

  override def deserialize(source: DataInputView): T = value

  override def deserialize(reuse: T, source: DataInputView): T = value

  override def copy(source: DataInputView, target: DataOutputView): Unit = {}

}

object FixedValueTypeInformationHelper {

  def nullValueTypeInfo[T: ClassTag]: BasicTypeInfo[T] = new BasicTypeInfo[T](
    implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]],
    Array.empty,
    NullSerializer.asInstanceOf[TypeSerializer[T]],
    null
  ) {}

  object NullSerializer extends FixedValueTypeInformationHelper[AnyRef] {
    override def value: AnyRef = null

    override def snapshotConfiguration(): TypeSerializerSnapshot[AnyRef] =
      new SimpleTypeSerializerSnapshot[AnyRef](new Supplier[TypeSerializer[AnyRef]] {
        override def get(): TypeSerializer[AnyRef] = NullSerializer
      }) {}

  }

}
