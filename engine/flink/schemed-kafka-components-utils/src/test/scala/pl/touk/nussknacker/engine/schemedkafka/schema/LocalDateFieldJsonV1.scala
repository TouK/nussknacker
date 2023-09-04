package pl.touk.nussknacker.engine.schemedkafka.schema

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LocalDateFieldJsonV1 extends TestSchemaWithRecord {

  override def exampleData: Map[String, Any] = exampleData(LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE))

  override def stringSchema: String = ???

  def jsonSchema: String = """{"type": "object", "properties": {"field": {"type": "string","format": "date"}}}"""

  def exampleData(date: LocalDate) = Map("field" -> date)

}
