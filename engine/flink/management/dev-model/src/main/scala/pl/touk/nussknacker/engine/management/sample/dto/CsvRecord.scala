package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.Json
import pl.touk.nussknacker.engine.api.DisplayJson

case class CsvRecord(fields: List[String]) extends DisplayJson {

  lazy val firstField: String = fields.head
  
  override def asJson: Json = Json.obj("firstField" -> Json.fromString(firstField))

  override def originalDisplay: Option[String] = Some(fields.mkString("|"))
}
