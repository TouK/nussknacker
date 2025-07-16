package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.lang.reflect.Type
import java.nio.charset.{Charset, StandardCharsets}
import java.util

class CharsetTypeInfoFactory extends TypeInfoFactory[Charset] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, TypeInformation[_]]
  ): TypeInformation[Charset] =
    CharsetTypeInfoFactory.typeInfo

}

object CharsetTypeInfoFactory {

  val typeInfo = new SimpleTypeInformation[Charset](new CharsetTypeSerializer)

}

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
