package pl.touk.nussknacker.engine.schemedkafka.schema

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ZoneDateTimeFieldJsonV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(
    ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
  )

  override def stringSchema: String = ???

  def jsonSchema: String = """{"type": "object", "properties": {"field": {"type": "string","format": "date-time"}}}"""

  def exampleData(dateTime: ZonedDateTime) = Map("field" -> dateTime)

}
