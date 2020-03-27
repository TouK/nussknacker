package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.Json
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.api.lazyy.UsingLazyValues

case class CsvRecord(fields: List[String]) extends UsingLazyValues with DisplayJson {

  lazy val firstField: String = fields.head

  lazy val enrichedField: LazyState[RichObject] = lazyValue[RichObject]("enricher", "param" -> firstField)

  override def asJson: Json = Json.obj("firstField" -> Json.fromString(firstField))

  override def originalDisplay: Option[String] = Some(fields.mkString("|"))
}
