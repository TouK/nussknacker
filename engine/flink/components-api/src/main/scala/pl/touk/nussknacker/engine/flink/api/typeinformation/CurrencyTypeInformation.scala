package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import java.util.Currency

object CurrencyTypeInformation extends SimpleTypeInformation[Currency](new CurrencyTypeSerializer)

class CurrencyTypeSerializer
    extends TypeSerializerSingleton[Currency]
    with ImmutableTypeSerializer[Currency]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Currency] {

  override def createInstance(): Currency = Currency.getInstance("USD")

  override def serialize(record: Currency, target: DataOutputView): Unit = target.writeUTF(record.getCurrencyCode)

  override def deserialize(source: DataInputView): Currency = Currency.getInstance(source.readUTF())

  override def snapshotConfiguration(): TypeSerializerSnapshot[Currency] =
    CurrencyTypeSerializer.CurrencyTypeSerializerSnapshot

}

object CurrencyTypeSerializer extends CurrencyTypeSerializer {

  class CurrencyTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Currency](() => CurrencyTypeSerializer)

  object CurrencyTypeSerializerSnapshot extends CurrencyTypeSerializerSnapshot

}
