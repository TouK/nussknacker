package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.avro.schema.AvroStringSettings

object AvroStringSettingsInTests {
  def enable(): Unit = setValue(true)

  def setDefault(): Unit = setValue(AvroStringSettings.default)

  def withStringEnabled[T](setting: Boolean)(execute : => T): T ={
    setValue(setting)
    try {
      execute
    } finally {
      setDefault()
    }
  }

  private def setValue(value: Boolean): Unit = {
    AvroStringSettings.forceUsingStringForStringSchema // initialize lazy value
    val field = Class.forName("pl.touk.nussknacker.engine.avro.schema.AvroStringSettings$").getDeclaredField("forceUsingStringForStringSchema")
    field.setAccessible(true)
    field.setBoolean(AvroStringSettings, value)
    field.setAccessible(false)
  }
}
