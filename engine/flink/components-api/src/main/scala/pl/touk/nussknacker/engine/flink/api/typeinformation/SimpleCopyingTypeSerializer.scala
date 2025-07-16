package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

trait SimpleCopyingTypeSerializer[T] { self: TypeSerializer[T] =>

  override def copy(source: DataInputView, target: DataOutputView): Unit = serialize(deserialize(source), target)

}
