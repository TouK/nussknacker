package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.time.{Instant, ZonedDateTime, ZoneOffset}

object ZonedDateTimeTypeInformation extends SimpleTypeInformation[ZonedDateTime](new ZonedDateTimeTypeSerializer)

class ZonedDateTimeTypeSerializer
    extends TypeSerializerSingleton[ZonedDateTime]
    with ImmutableTypeSerializer[ZonedDateTime]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[ZonedDateTime] {

  override def createInstance(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC)

  override def serialize(record: ZonedDateTime, target: DataOutputView): Unit =
    target.writeUTF(record.toString)

  override def deserialize(source: DataInputView): ZonedDateTime = ZonedDateTime.parse(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[ZonedDateTime] =
    ZonedDateTimeTypeSerializer.ZonedDateTimeTypeSerializerSnapshot
}

object ZonedDateTimeTypeSerializer extends ZonedDateTimeTypeSerializer {

  object ZonedDateTimeTypeSerializerSnapshot extends ZonedDateTimeTypeSerializerSnapshot

  class ZonedDateTimeTypeSerializerSnapshot
      extends SimpleTypeSerializerSnapshot[ZonedDateTime](() => ZonedDateTimeTypeSerializer)

}
