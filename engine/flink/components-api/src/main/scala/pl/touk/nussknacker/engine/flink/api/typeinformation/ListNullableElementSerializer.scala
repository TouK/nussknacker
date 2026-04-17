package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.annotation.Internal
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.util

/** Serializer for [[java.util.List]] that handles null elements.
  *
  * Wire format per element: boolean (true = value present, false = null), followed by element bytes when present.
  * This differs from Flink's [[org.apache.flink.api.common.typeutils.base.ListSerializer]] which does not support
  * null elements.
  */
@Internal
class ListNullableElementSerializer[T <: AnyRef](val elementSerializer: TypeSerializer[T])
    extends TypeSerializer[util.List[T]] {

  override def isImmutableType: Boolean = false

  override def duplicate(): TypeSerializer[util.List[T]] =
    new ListNullableElementSerializer[T](elementSerializer.duplicate())

  override def createInstance(): util.List[T] = new util.ArrayList[T]()

  override def copy(from: util.List[T]): util.List[T] = {
    val result = new util.ArrayList[T](from.size())
    from.forEach(element => result.add(if (element == null) null.asInstanceOf[T] else elementSerializer.copy(element)))
    result
  }

  override def copy(from: util.List[T], reuse: util.List[T]): util.List[T] = copy(from)

  override def getLength: Int = -1

  override def serialize(record: util.List[T], target: DataOutputView): Unit = {
    target.writeInt(record.size())
    record.forEach { element =>
      if (element == null) {
        target.writeBoolean(false)
      } else {
        target.writeBoolean(true)
        elementSerializer.serialize(element, target)
      }
    }
  }

  override def deserialize(source: DataInputView): util.List[T] = {
    val size   = source.readInt()
    val result = new util.ArrayList[T](size)
    (0 until size).foreach { _ =>
      result.add(if (source.readBoolean()) elementSerializer.deserialize(source) else null.asInstanceOf[T])
    }
    result
  }

  override def deserialize(reuse: util.List[T], source: DataInputView): util.List[T] = deserialize(source)

  override def copy(source: DataInputView, target: DataOutputView): Unit = {
    val size = source.readInt()
    target.writeInt(size)
    (0 until size).foreach { _ =>
      val hasValue = source.readBoolean()
      target.writeBoolean(hasValue)
      if (hasValue) elementSerializer.copy(source, target)
    }
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: ListNullableElementSerializer[_] => elementSerializer.equals(other.elementSerializer)
    case _                                       => false
  }

  override def hashCode(): Int = elementSerializer.hashCode()

  override def snapshotConfiguration(): TypeSerializerSnapshot[util.List[T]] =
    new ListNullableElementSerializerSnapshot[T](this)

}
