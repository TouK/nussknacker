package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.time.{Instant, OffsetDateTime, ZoneOffset}

object OffsetDateTimeTypeInformation extends SimpleTypeInformation[OffsetDateTime](new OffsetDateTimeTypeSerializer)

class OffsetDateTimeTypeSerializer
    extends TypeSerializerSingleton[OffsetDateTime]
    with ImmutableTypeSerializer[OffsetDateTime]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[OffsetDateTime] {

  override def createInstance(): OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC)

  override def serialize(record: OffsetDateTime, target: DataOutputView): Unit =
    target.writeUTF(record.toString)

  override def deserialize(source: DataInputView): OffsetDateTime = OffsetDateTime.parse(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[OffsetDateTime] =
    OffsetDateTimeTypeSerializer.OffsetDateTimeTypeSerializerSnapshot
}

object OffsetDateTimeTypeSerializer extends OffsetDateTimeTypeSerializer {

  object OffsetDateTimeTypeSerializerSnapshot extends OffsetDateTimeTypeSerializerSnapshot

  class OffsetDateTimeTypeSerializerSnapshot
      extends SimpleTypeSerializerSnapshot[OffsetDateTime](() => OffsetDateTimeTypeSerializer)

}
