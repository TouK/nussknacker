package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.time.{ZoneId, ZoneOffset}

object ZoneIdTypeInformation extends SimpleTypeInformation[ZoneId](new ZoneIdTypeSerializer)

class ZoneIdTypeSerializer
    extends TypeSerializerSingleton[ZoneId]
    with ImmutableTypeSerializer[ZoneId]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[ZoneId] {

  override def createInstance(): ZoneId = ZoneOffset.UTC

  override def serialize(record: ZoneId, target: DataOutputView): Unit = target.writeUTF(record.getId)

  override def deserialize(source: DataInputView): ZoneId = ZoneId.of(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[ZoneId] =
    ZoneIdTypeSerializer.ZoneIdTypeSerializerSnapshot

}

object ZoneIdTypeSerializer extends ZoneIdTypeSerializer {

  class ZoneIdTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[ZoneId](() => ZoneIdTypeSerializer)

  object ZoneIdTypeSerializerSnapshot extends ZoneIdTypeSerializerSnapshot

}
