package pl.touk.nussknacker.ui.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.contrib.json.classic.JsonLayout

import java.util

// Setters and classes with empty constructor are required by logback
class JsonLayoutWithCustomField extends JsonLayout {
  private var customField: JsonLayoutCustomField = null

  def setCustomField(customField: JsonLayoutCustomField): Unit =
    this.customField = customField

  override def addCustomDataToJsonMap(map: util.Map[String, AnyRef], event: ILoggingEvent): Unit = {
    if (customField != null && customField.getKey() != null && customField.getValue() != null) {
      map.put(customField.getKey(), customField.getValue())
    }
  }

}

class JsonLayoutCustomField {
  private var key: String   = null
  private var value: String = null

  def setKey(key: String): Unit =
    this.key = key

  def setValue(value: String): Unit =
    this.value = value

  def getKey(): String   = key
  def getValue(): String = value
}
