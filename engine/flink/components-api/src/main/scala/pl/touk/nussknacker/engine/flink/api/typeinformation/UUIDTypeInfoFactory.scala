package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.lang.reflect.Type
import java.util
import java.util.UUID

class UUIDTypeInfoFactory extends TypeInfoFactory[UUID] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, TypeInformation[_]]
  ): TypeInformation[UUID] =
    UUIDTypeInfoFactory.typeInfo

}

object UUIDTypeInfoFactory {

  val typeInfo = new SimpleTypeInformation[UUID](new UUIDTypeSerializer)

}

class UUIDTypeSerializer
    extends TypeSerializerSingleton[UUID]
    with ImmutableTypeSerializer[UUID]
    with SimpleCopyingTypeSerializer[UUID] {

  override def createInstance(): UUID = new UUID(0, 0)

  override def serialize(record: UUID, target: DataOutputView): Unit = {
    target.writeLong(record.getMostSignificantBits)
    target.writeLong(record.getLeastSignificantBits)
  }

  override def deserialize(source: DataInputView): UUID = {
    val mostSignificantBits  = source.readLong()
    val leastSignificantBits = source.readLong()
    new UUID(mostSignificantBits, leastSignificantBits)
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[UUID] =
    UUIDTypeSerializer.UUIDTypeSerializerSnapshot

  override def getLength: Int = java.lang.Long.BYTES * 2

}

object UUIDTypeSerializer extends UUIDTypeSerializer {

  class UUIDTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[UUID](() => UUIDTypeSerializer)

  object UUIDTypeSerializerSnapshot extends UUIDTypeSerializerSnapshot

}
