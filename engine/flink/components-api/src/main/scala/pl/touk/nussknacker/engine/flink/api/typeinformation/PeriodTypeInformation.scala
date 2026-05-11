package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import pl.touk.nussknacker.engine.flink.api.typeinformation.PeriodTypeSerializer.PeriodTypeSerializerSnapshot

import java.time.Period

object PeriodTypeInformation extends SimpleTypeInformation[Period](new PeriodTypeSerializer)

class PeriodTypeSerializer
    extends TypeSerializerSingleton[Period]
    with ImmutableTypeSerializer[Period]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Period] {

  override def createInstance(): Period = Period.ZERO

  override def serialize(period: Period, target: DataOutputView): Unit = target.writeUTF(period.toString)

  override def deserialize(source: DataInputView): Period = Period.parse(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[Period] = PeriodTypeSerializerSnapshot

}

object PeriodTypeSerializer extends PeriodTypeSerializer {

  class PeriodTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Period](() => PeriodTypeSerializer)

  object PeriodTypeSerializerSnapshot extends PeriodTypeSerializerSnapshot

}
