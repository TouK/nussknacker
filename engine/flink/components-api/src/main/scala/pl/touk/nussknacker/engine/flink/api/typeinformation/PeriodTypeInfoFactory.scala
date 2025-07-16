package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import pl.touk.nussknacker.engine.flink.api.typeinformation.PeriodTypeSerializer.PeriodTypeSerializerSnapshot

import java.lang.reflect.Type
import java.time.Period
import java.util

class PeriodTypeInfoFactory extends TypeInfoFactory[Period] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, TypeInformation[_]]
  ): TypeInformation[Period] =
    new SimpleTypeInformation[Period](new PeriodTypeSerializer)

}

class PeriodTypeSerializer
    extends TypeSerializerSingleton[Period]
    with ImmutableTypeSerializer[Period]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Period] {

  override def createInstance(): Period = Period.ZERO

  override def serialize(period: Period, target: DataOutputView): Unit = target.writeUTF(period.toString)

  override def deserialize(source: DataInputView): Period = Period.parse(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[Period] = new PeriodTypeSerializerSnapshot

}

object PeriodTypeSerializer {

  private val instance = new PeriodTypeSerializer

  class PeriodTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Period](() => instance)

}
