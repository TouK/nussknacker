package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.nio.charset.{Charset, StandardCharsets}

object CharsetTypeInformation extends SimpleTypeInformation[Charset](new CharsetTypeSerializer)

class CharsetTypeSerializer
    extends TypeSerializerSingleton[Charset]
    with ImmutableTypeSerializer[Charset]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Charset] {

  override def createInstance(): Charset = StandardCharsets.UTF_8

  override def serialize(record: Charset, target: DataOutputView): Unit = target.writeUTF(record.name())

  override def deserialize(source: DataInputView): Charset = Charset.forName(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[Charset] =
    CharsetTypeSerializer.CharsetTypeSerializerSnapshot
}

object CharsetTypeSerializer extends CharsetTypeSerializer {

  class CharsetTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Charset](() => CharsetTypeSerializer)

  object CharsetTypeSerializerSnapshot extends CharsetTypeSerializerSnapshot

}
