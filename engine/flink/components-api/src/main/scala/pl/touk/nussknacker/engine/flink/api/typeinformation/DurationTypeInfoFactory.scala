package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.lang.reflect.Type
import java.time.Duration
import java.util

class DurationTypeInfoFactory extends TypeInfoFactory[Duration] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, TypeInformation[_]]
  ): TypeInformation[Duration] =
    DurationTypeInfoFactory.typeInfo

}

object DurationTypeInfoFactory {

  val typeInfo = new SimpleTypeInformation[Duration](new DurationTypeSerializer)

}

class DurationTypeSerializer
    extends TypeSerializerSingleton[Duration]
    with ImmutableTypeSerializer[Duration]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Duration] {

  override def createInstance(): Duration = Duration.ZERO

  override def serialize(record: Duration, target: DataOutputView): Unit = target.writeUTF(record.toString)

  override def deserialize(source: DataInputView): Duration = Duration.parse(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[Duration] =
    DurationTypeSerializer.DurationTypeSerializerSnapshot

}

object DurationTypeSerializer extends DurationTypeSerializer {

  class DurationTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Duration](() => DurationTypeSerializer)

  object DurationTypeSerializerSnapshot extends DurationTypeSerializerSnapshot

}
