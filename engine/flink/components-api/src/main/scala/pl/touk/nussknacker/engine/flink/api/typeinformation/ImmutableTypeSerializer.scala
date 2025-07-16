package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.DataInputView

trait ImmutableTypeSerializer[T] { self: TypeSerializerSingleton[T] =>

  override def isImmutableType: Boolean = true

  override def copy(from: T): T = from

  override def copy(from: T, reuse: T): T = from

  override def deserialize(reuse: T, source: DataInputView): T = self.deserialize(source)

}
