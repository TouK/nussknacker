package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.commons.lang3.LocaleUtils
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializerSnapshot}
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.springframework.util.StringUtils

import java.util.Locale

object LocaleTypeInformation extends SimpleTypeInformation[Locale](new LocaleTypeSerializer)

class LocaleTypeSerializer
    extends TypeSerializerSingleton[Locale]
    with ImmutableTypeSerializer[Locale]
    with DynamicLengthSerializer
    with SimpleCopyingTypeSerializer[Locale] {

  override def createInstance(): Locale = Locale.ENGLISH

  override def serialize(record: Locale, target: DataOutputView): Unit = target.writeUTF(record.toString)

  override def deserialize(source: DataInputView): Locale = {
    val locale = StringUtils.parseLocale(source.readUTF())
    assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
    locale
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[Locale] =
    LocaleTypeSerializer.LocaleTypeSerializerSnapshot

}

object LocaleTypeSerializer extends LocaleTypeSerializer {

  class LocaleTypeSerializerSnapshot extends SimpleTypeSerializerSnapshot[Locale](() => LocaleTypeSerializer)

  object LocaleTypeSerializerSnapshot extends LocaleTypeSerializerSnapshot

}
