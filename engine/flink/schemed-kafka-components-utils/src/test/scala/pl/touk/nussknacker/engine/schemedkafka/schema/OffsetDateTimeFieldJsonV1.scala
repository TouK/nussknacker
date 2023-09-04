package pl.touk.nussknacker.engine.schemedkafka.schema

import java.time.{OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

object OffsetDateTimeFieldJsonV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME).toOffsetDateTime)

  override def stringSchema: String = ???

  def jsonSchema: String = """{"type": "object", "properties": {"field": {"type": "string","format": "date-time"}}}"""

  def exampleData(dateTime: OffsetDateTime) = Map("field" -> dateTime)

}
